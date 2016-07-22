package com.leanote.android.service;

import android.net.Uri;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.leanote.android.Leanote;
import com.leanote.android.model.AccountHelper;
import com.leanote.android.model.NoteInfo;
import com.leanote.android.model.NoteDetailList;
import com.leanote.android.model.NotebookInfo;
import com.leanote.android.networking.NetworkRequest;
import com.leanote.android.task.DownloadMediaTask;
import com.leanote.android.util.AppLog;
import com.leanote.android.util.MediaFile;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by binnchx on 11/2/15.
 */
public class NoteSyncService {

    private static final int MAX_SYNC_SIZE = 20;

    private static OnNotebooksSyncListener notebooksSyncListener;


    public static void syncPullNote() {
        int lastSyncUsn = AccountHelper.getDefaultAccount().getLastSyncUsn();

        int serverUsn = getServerSyncState();

        boolean ifNeedSync = lastSyncUsn < serverUsn;

        AppLog.i("needsync:[localsync:" + lastSyncUsn + ", serverUsn:" + serverUsn + "]");

        if (ifNeedSync) {
            String host = AccountHelper.getDefaultAccount().getHost();

            String noteApi = String.format("%s/api/note/getSyncNotes?token=%s&maxEntry=%s",
                    host, AccountHelper.getDefaultAccount().getAccessToken(), MAX_SYNC_SIZE);

            getSyncNote(noteApi, lastSyncUsn);

            String notebookApi = String.format("%s/api/notebook/getNotebooks?token=%s&maxEntry=%s",
                    host, AccountHelper.getDefaultAccount().getAccessToken(), MAX_SYNC_SIZE);

            getSyncNotebook(notebookApi, lastSyncUsn);

            if (Leanote.isFirstSync()) {
                //第一次全量pull笔记时才更新user usn
                Leanote.leaDB.updateAccountUsn(serverUsn, AccountHelper.getDefaultAccount().getUserId());
                Leanote.setIsFirstSync(false);
            }
        }

    }

    private static void getSyncNotebook(String apiPrefix, int lastSyncUsn) {

        String notebookApi;
        if (lastSyncUsn == 0) {
            notebookApi = apiPrefix;
        } else {
            notebookApi = apiPrefix + "&afterUsn=" + lastSyncUsn;
        }


        List<String> localNotebookIds = Leanote.leaDB.getLocalNotebookIds(AccountHelper.getDefaultAccount().getUserId());
        try {
            for (int m = 0; m < Integer.MAX_VALUE; m++) {

                String response = NetworkRequest.syncGetRequest(notebookApi);
                JSONArray jsonArray = new JSONArray(response);

                updateNotebookToLocal(jsonArray, localNotebookIds);

                if (jsonArray.length() == MAX_SYNC_SIZE) {
                    int afterUsn = jsonArray.getJSONObject(MAX_SYNC_SIZE - 1).getInt("Usn");
                    notebookApi = apiPrefix + "&afterUsn=" + afterUsn;
                } else {
                    break;
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void updateNotebookToLocal(JSONArray array, List<String> localNotebookIds) throws Exception {
        List<NotebookInfo> newNotebooks = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);

            boolean isDeleted = item.getBoolean("IsDeleted");
            String notebookId = item.getString("NotebookId");
            if (isDeleted) {
                Leanote.leaDB.deleteNotebook(notebookId);
                continue;
            }

            NotebookInfo serverNotebook = parseServerNotebook(item);

            if (localNotebookIds.contains(notebookId)) {
                if (Leanote.leaDB.getLocalNotebookByNotebookId(notebookId).isDirty()) {
                    //conflict
                    AppLog.i("conflict :" + notebookId);
                } else {
                    //更新本地笔记
                    Leanote.leaDB.updateNotebook(serverNotebook);
                }
            } else {
                //本地新增笔记
                newNotebooks.add(serverNotebook);

            }
        }

        Leanote.leaDB.saveNotebooks(newNotebooks);
    }

    public static NotebookInfo parseServerNotebook(JSONObject item) throws JSONException {
        NotebookInfo serverNotebook = new NotebookInfo();

        serverNotebook.setNotebookId(item.getString("NotebookId"));
        serverNotebook.setTitle(item.getString("Title"));
        serverNotebook.setUserId(item.getString("UserId"));
        serverNotebook.setParentNotebookId(item.getString("ParentNotebookId"));
        serverNotebook.setSeq(item.getInt("Seq"));
        serverNotebook.setUrlTitle(item.getString("UrlTitle"));
        serverNotebook.setIsBlog(item.getBoolean("IsBlog"));
        serverNotebook.setCreateTime(item.getString("CreatedTime"));
        serverNotebook.setUpdateTime(item.getString("UpdatedTime"));
        serverNotebook.setUsn(item.getInt("Usn"));
        serverNotebook.setIsDeleted(item.getBoolean("IsDeleted"));

        return serverNotebook;
    }


    public static int getServerSyncState() {

        String host = AccountHelper.getDefaultAccount().getHost();
        String noteApi = String.format("%s/api/user/getSyncState?token=%s", host,
                AccountHelper.getDefaultAccount().getAccessToken());

        try {
            String response = NetworkRequest.syncGetRequest(noteApi);
            JSONObject json = new JSONObject(response);
            AppLog.i("sync state:" + json);
            int serverUsn = json.getInt("LastSyncUsn");

            return serverUsn;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }

    }


    public static NoteInfo getServerNote(String noteId) {
        String api = String.format("%s/api/note/getNoteAndContent?token=%s&noteId=%s",
                AccountHelper.getDefaultAccount().getHost(),
                AccountHelper.getDefaultAccount().getAccessToken(),
                noteId);

        NoteInfo note = new NoteInfo();

        try {
            String response = NetworkRequest.syncGetRequest(api);
            JSONObject json = new JSONObject(response);

            note.setNoteId(json.getString("NoteId"));
            note.setNoteBookId(json.getString("NotebookId"));
            note.setUserId(json.getString("UserId"));
            note.setTitle(json.getString("Title"));
            note.setDesc(json.getString("Desc"));

            String tags = json.getString("Tags");
            AppLog.i("server tags:" + tags);

            note.setTags(parseTags(tags));
            note.setNoteAbstract(json.getString("Abstract"));
            note.setContent(json.getString("Content"));
            note.setIsMarkDown(json.getBoolean("IsMarkdown"));
            note.setIsPublicBlog(json.getBoolean("IsBlog"));
            note.setIsTrash(json.getBoolean("IsTrash"));
            note.setIsDeleted(json.getBoolean("IsDeleted"));
            note.setUsn(json.getInt("Usn"));
            AppLog.i("conflict, usn:" + json.getInt("Usn"));

            JSONArray fileArray = json.getJSONArray("Files");
            List<String> fileIdList = new ArrayList<>();
            for (int i = 0; i < fileArray.length(); i++) {
                JSONObject item = fileArray.getJSONObject(i);
                fileIdList.add(item.getString("LocalFileId"));
            }
            note.setFileIds(StringUtils.join(fileIdList, ","));
            note.setCreatedTime(json.getString("CreatedTime"));
            note.setUpdatedTime(json.getString("UpdatedTime"));
            note.setPublicTime(json.getString("PublicTime"));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return note;
    }

    public static void getSyncNote(String apiPrefix, int lastSyncUsn) {

        String noteApi;

        if (lastSyncUsn == 0) {
            noteApi = apiPrefix;
        } else {
            noteApi = apiPrefix + "&afterUsn=" + lastSyncUsn;
        }

        try {
            for (int m = 0; m < Integer.MAX_VALUE; m++) {
                String response = NetworkRequest.syncGetRequest(noteApi);

                JSONArray jsonArray = new JSONArray(response);

                List<String> localNoteIds = Leanote.leaDB.getLocalNoteIds(AccountHelper.getDefaultAccount().getUserId());
                updateNoteToLocal(jsonArray, localNoteIds);

                if (jsonArray.length() == MAX_SYNC_SIZE) {
                    int afterUsn = jsonArray.getJSONObject(MAX_SYNC_SIZE - 1).getInt("Usn");
                    noteApi = apiPrefix + "&afterUsn=" + afterUsn;
                } else {
                    break;
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    private static void processNoteMedia(String content) {
        //note upload 前需要更新图片链接
        String imageTagsPattern = "<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>";
        String attachPattern = "";
        Pattern pattern = Pattern.compile(imageTagsPattern);
        Matcher matcher = pattern.matcher(content);

        List<String> imageTags = new ArrayList<>();
        while (matcher.find()) {
            imageTags.add(matcher.group());
        }

        for (String tag : imageTags) {
            Pattern p = Pattern.compile("src\\s*=\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(tag);
            if (m.find()) {
                String imageUrl = m.group(1);
                if (!"".equals(imageUrl)) {

                    String[] fileIdArr = imageUrl.split("fileId=");
                    if (fileIdArr.length < 2) {
                        AppLog.i("imageurl:" + imageUrl);
                        continue;
                    }

                    String fileId = fileIdArr[1];
                    MediaFile mediaFile = Leanote.leaDB.getMediaFileByFileId(fileId);

                    if (mediaFile == null) {
                        DownloadMediaTask downloadMediaTask = new DownloadMediaTask(imageUrl, null);
                        downloadMediaTask.execute(Uri.parse(imageUrl));
                    }
                }
            }
        }

    }


    private static void updateNoteToLocal(JSONArray jsonArray, List<String> localNoteIds) throws Exception {
        NoteDetailList syncNotes = new NoteDetailList();

        int count = 0;
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject item = jsonArray.getJSONObject(i);

            NoteInfo note = null;
            try {
                note = parseServerNote(item, localNoteIds);
            } catch (Exception e) {
                Log.e("fetch note error", ":", e);
            }

            if (note != null) {
                AppLog.i("add server note succ:" + (++count));
                syncNotes.add(note);
            }

        }
        Leanote.leaDB.saveNotes(syncNotes);
    }

    public static NoteInfo parseServerNote(JSONObject item, List<String> localNoteIds)
            throws JSONException, ExecutionException, InterruptedException {

        boolean isDeleted = item.getBoolean("IsDeleted");
        boolean isTrash = item.getBoolean("IsTrash");
        String noteId = item.getString("NoteId");

        if (isDeleted || isTrash) {
            AppLog.i("server note is deleted or trashed");
            Leanote.leaDB.deleteNoteByNoteId(noteId);
            return null;
        }

        NoteInfo serverNote = new NoteInfo();

        serverNote.setNoteId(noteId);
        serverNote.setNoteBookId(item.getString("NotebookId"));
        String userId = item.getString("UserId");
        serverNote.setUserId(userId);
        serverNote.setTitle(item.getString("Title"));
        serverNote.setCreatedTime(item.getString("CreatedTime"));
        serverNote.setIsMarkDown(item.getBoolean("IsMarkdown"));

        String tags = item.getString("Tags");
        serverNote.setTags(parseTags(tags));

        serverNote.setIsPublicBlog(item.getBoolean("IsBlog"));
        serverNote.setUpdatedTime(item.getString("UpdatedTime"));
        serverNote.setPublicTime(item.getString("PublicTime"));
        serverNote.setUserId(item.getString("UserId"));
        serverNote.setDesc(item.getString("Desc"));
        serverNote.setNoteAbstract(item.getString("Abstract"));


        String host = AccountHelper.getDefaultAccount().getHost();
        String noteContentApi = String.format("%s/api/note/getNoteAndContent?token=%s&noteId=%s", host,
                AccountHelper.getDefaultAccount().getAccessToken(), noteId);

        String contentRes = NetworkRequest.syncGetRequest(noteContentApi);

        AppLog.i("get note content:" + contentRes);
        if (TextUtils.isEmpty(contentRes)) {
            serverNote.setContent("");
        } else {
            JSONObject json = new JSONObject(contentRes);

            String noteContent = json.getString("Content");
            serverNote.setContent(noteContent);
            processNoteMedia(noteContent);
        }

        serverNote.setUsn(item.getInt("Usn"));

        if (localNoteIds.contains(noteId)) {
            if (Leanote.leaDB.getLocalNoteByNoteId(noteId).isDirty()) {
                //conflict
                AppLog.i("conflict :" + noteId);
                serverNote.setTitle(item.getString("Title") + "---push-conflict");
                return serverNote;
            } else {
                //更新本地笔记
                AppLog.i("update server note to local:");
                serverNote.setIsDirty(false);

                //Leanote.leaDB.updateNote(serverNote);
                Leanote.leaDB.updateNoteByNoteId(serverNote);
                return null;
            }

        } else {
            //本地新增笔记
            AppLog.i("add server note :" + noteId);
            //syncNotes.add(serverNote);
            serverNote.setIsDeleted(false);
            return serverNote;
        }
        //return null;
    }

    private static String parseTags(String tags) throws JSONException {
        JSONArray tagArray = null;
        try {
            tagArray = new JSONArray(tags);
        } catch (JSONException e) {
            return "";
        }
        StringBuilder tagBuilder = new StringBuilder();
        for (int i = 0; i < tagArray.length(); i++) {
            tagBuilder.append(tagArray.get(i));
            if (i < (tagArray.length() - 1)) {
                tagBuilder.append(",");
            }
        }

        return tagBuilder.toString();
    }


    public static void sendNotebookChanges() throws ExecutionException, InterruptedException {
        List<NotebookInfo> dirtyNotebooks = Leanote.leaDB.getDirtyNotebooks();
        String host = AccountHelper.getDefaultAccount().getHost();
        String token = AccountHelper.getDefaultAccount().getAccessToken();


        List<String> notebookApis = new ArrayList<>();
        for (NotebookInfo notebook : dirtyNotebooks) {
            String notebookId = notebook.getNotebookId();

            String api;
            if (TextUtils.isEmpty(notebookId)) {
                api = String.format("%s/api/notebook/addNotebook?token=%s&title=%s", host, token,
                        notebook.getTitle());

            } else if (notebook.isDeleted()) {
                api = String.format("%s/api/notebook/deleteNotebook?token=%s&notebookId=%s&usn=%s", host, token,
                        notebookId, notebook.getUsn());

            } else {
                api = String.format("%s/api/notebook/updateNotebook?token=%s&notebookId=%s&title=%s&usn=%s", host,
                        token, notebookId, notebook.getTitle(), notebook.getUsn());

            }

            notebookApis.add(api);

        }
        new SyncNotebooksTask(notebookApis).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }


    private static class SyncNotebooksTask extends AsyncTask<Void, Void, Boolean> {
        private List<String> notebookApis;

        public SyncNotebooksTask(List<String> notebookApis) {
            this.notebookApis = notebookApis;
        }


        @Override
        protected Boolean doInBackground(Void... params) {
            boolean succ = true;
            for (String api : notebookApis) {
                try {
                    String response = NetworkRequest.syncGetRequest(api);
                    JSONObject json = new JSONObject(response);
                    String notebookId = json.getString("NotebookId");

                    if (json.getBoolean("Ok") || !TextUtils.isEmpty(notebookId)) {
                        //更新成功
                        NotebookInfo notebook = new NotebookInfo();
                        notebook.setNotebookId(json.getString("NotebookId"));
                        notebook.setUserId(json.getString("UserId"));
                        notebook.setSeq(json.getInt("Seq"));
                        notebook.setTitle(json.getString("Title"));
                        notebook.setIsBlog(json.getBoolean("IsBlog"));
                        notebook.setIsDeleted(json.getBoolean("IsDeleted"));
                        notebook.setCreateTime(json.getString("CreatedTime"));
                        notebook.setUpdateTime(json.getString("UpdatedTime"));
                        notebook.setUsn(json.getInt("Usn"));
                        notebook.setIsDirty(false);

                        Leanote.leaDB.updateNotebook(notebook);
                    } else {
                        //处理冲突
                        succ = false;
                        //冲突，重新拉取server notebook更新到本地
                        String notebookApi = String.format("%s/api/notebook/getNotebooks?token=%s",
                                AccountHelper.getDefaultAccount().getHost(),
                                AccountHelper.getDefaultAccount().getAccessToken());

                        String notebookRes = NetworkRequest.syncGetRequest(notebookApi);
                        JSONArray jsonArray = new JSONArray(notebookRes);
                        JSONObject notebook = null;

                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject notebookObj = jsonArray.getJSONObject(i);
                            if (StringUtils.equals(notebookId, notebookObj.getString("NotebookId"))) {
                                notebook = notebookObj;
                            }
                        }

                        NotebookInfo serverNotebook = parseServerNotebook(notebook);
                        Leanote.leaDB.updateNotebook(serverNotebook);

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    succ = false;
                }
            }
            return succ;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }


        @Override
        protected void onPostExecute(Boolean result) {
            notebooksSyncListener.onNotebooksPullDone(result);
        }
    }

    public interface OnNotebooksSyncListener {
        void onNotebooksPullDone(Boolean result);
    }


    public interface OnNotebsSyncListener {
        void onNotesSync(int postCount);
    }

    public static void updateNotebook(NotebookInfo notebook) throws Exception {
        //本地新增笔记本，发送变化到服务端
        Leanote.leaDB.updateNotebook(notebook);
        sendNotebookChanges();
    }

    public static void setNotebooksSyncListener(OnNotebooksSyncListener notebooksSyncListener) {
        NoteSyncService.notebooksSyncListener = notebooksSyncListener;
    }
}
