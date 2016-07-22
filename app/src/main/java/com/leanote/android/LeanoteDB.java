package com.leanote.android;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import com.leanote.android.datasets.AccountTable;
import com.leanote.android.model.AccountHelper;
import com.leanote.android.model.NoteInfo;
import com.leanote.android.model.NoteDetailList;
import com.leanote.android.model.NotebookInfo;
import com.leanote.android.util.AppLog;
import com.leanote.android.util.MediaFile;
import com.leanote.android.util.SqlUtils;

import java.util.ArrayList;
import java.util.List;

public class LeanoteDB extends SQLiteOpenHelper {

    public static final String COLUMN_NAME_ID                    = "id";
    public static final String COLUMN_NAME_NOTE_ID               = "noteID";
    public static final String COLUMN_NAME_FILE_PATH             = "filePath";
    public static final String COLUMN_NAME_FILE_NAME             = "fileName";
    public static final String COLUMN_NAME_TITLE                 = "title";
    public static final String COLUMN_NAME_DESCRIPTION           = "description";
    public static final String COLUMN_NAME_CAPTION               = "caption";
    public static final String COLUMN_NAME_HORIZONTAL_ALIGNMENT  = "horizontalAlignment";
    public static final String COLUMN_NAME_WIDTH                 = "width";
    public static final String COLUMN_NAME_HEIGHT                = "height";
    public static final String COLUMN_NAME_MIME_TYPE             = "mimeType";
    public static final String COLUMN_NAME_FILE_URL              = "fileURL";
    public static final String COLUMN_NAME_THUMBNAIL_URL         = "thumbnailURL";
    public static final String COLUMN_NAME_MEDIA_ID              = "mediaId";
    public static final String COLUMN_NAME_UPLOAD_STATE          = "uploadState";
    private static final int DATABASE_VERSION = 1;

    private static final String DATABASE_NAME = "leanote";

    private static final String CREATE_TABLE_NOTES =
        "create table if not exists notes ("
            + "id integer primary key autoincrement,"
            + "noteId text,"
            + "notebookId text,"
            + "userId text,"
            + "title text default '',"
            + "tags text default '',"
            + "content text default '',"
            + "isMarkDown integer default 0,"
            + "isBlog integer default 0,"
            + "isTrash integer default 0,"
            + "files text default '',"
            + "createdTime text default '',"
            + "updatedTime text default '',"
            + "publicTime text default '',"
            + "usn  integer default 0,"
            + "desc text default '',"
            + "note_abstract default '',"
            + "is_dirty integer default 0,"
            + "isUploading integer default 0)";

//    private static final String CREATE_TABLE_NOTE_CONTENT =
//            "create table if not exists note_content ("
//                    + "noteId text primary key,"
//                    + "userId text,"
//                    + "content text);";


    private static final String CREATE_TABLE_NOTEBOOKS =
            "create table if not exists notebooks ("
                    + "id integer primary key autoincrement,"
                    + "notebookId text,"
                    + "parentNotebookId text,"
                    + "userId text,"
                    + "title text default '',"
                    + "urlTitle text default '',"
                    + "isBlog integer default 0,"
                    + "isTrash integer default 0,"
                    + "isDeleted integer default 0,"
                    + "createdTime text default '',"
                    + "updatedTime text default '',"
                    + "usn integer,"
                    + "is_dirty integer default 0)";

    private static final String NOTES_TABLE = "notes";

    private static final String ACCOUNTS_TABLE = "accounts";

    private static final String NOTEBOOKS_TABLE = "notebooks";

    //private static final String NOTE_CONTENT_TABLE = "note_content";

    private static final String MEDIA_TABLE = "media";

    private static final String CREATE_TABLE_MEDIA = "create table if not exists media "
            + "(id text primary key, "
            + "mediaID text default '',"
            + "noteID text default '', filePath text default '', "
            + "fileName text default '', title text default '', "
            + "fileURL text default '',"
            + "thumbnailURL text default '',"
            + "uploadState text default '',"
            + "description text default '', caption text default '', "
            + "horizontalAlignment integer default 0, width integer default 0, "
            + "height integer default 0, mimeType text default '');";


    private SQLiteDatabase db;

    private Context context;

    public SQLiteDatabase getDatabase() {
        return db;
    }

    public LeanoteDB(Context ctx) {
        super(ctx, DATABASE_NAME, null, DATABASE_VERSION);

        //db = ctx.openOrCreateDatabase(DATABASE_NAME, 0, null);
        db = this.getWritableDatabase();

    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_NOTES);
        db.execSQL(CREATE_TABLE_NOTEBOOKS);
        db.execSQL(CREATE_TABLE_MEDIA);

        AccountTable.createTables(db);
    }



    public static void deleteDatabase(Context ctx) {
        ctx.deleteDatabase(DATABASE_NAME);
    }

    public void saveNotes(List<?> notesList) {
        List<String> noteIds = getLocalNoteIds(AccountHelper.getDefaultAccount().getUserId());
        if (notesList != null && notesList.size() != 0) {
            db.beginTransaction();
            try {
                for (int i = 0; i < notesList.size(); i++) {

                    NoteInfo note = (NoteInfo) notesList.get(i);

                    String noteId = note.getNoteId();
                    if (noteIds.contains(noteId)) {
                        continue;
                    }

                    ContentValues values = getContentValuesFromNote(note);

                    db.insert(NOTES_TABLE, null, values);
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }


    public NoteDetailList getNotesList(String userId) {
        NoteDetailList listPosts = new NoteDetailList();

        String[] args = {userId};
        Cursor c = db.query(NOTES_TABLE, null, "userId=?", args, null, null, "");
        try {
            while (c.moveToNext()) {
                NoteInfo detail = fillNote(c);
                listPosts.add(detail);
            }
            return listPosts;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }

    public List<String> getLocalNoteIds(String userId) {

        String[] args = {userId};
        //Cursor c = db.query(NOTES_TABLE, null, null, null, null, null, "");
        Cursor c = db.query(NOTES_TABLE, null, "userId=?", args, null, null, "");
        List<String> noteIds = new ArrayList<>();
        try {
            while (c.moveToNext()) {

                noteIds.add(c.getString(1));
            }
            return noteIds;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public NoteInfo getLocalNoteById(long localNoteId) {
        String[] args = {String.valueOf(localNoteId)};
        //Cursor c = db.query(NOTES_TABLE, null, null, null, null, null, "");
        Cursor c = db.query(NOTES_TABLE, null, "id=?", args, null, null, "");

        NoteInfo detail = null;
        try {
            if (c.moveToNext()) {
                detail = fillNote(c);
            }
            return detail;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    private NoteInfo fillNote(Cursor c) {
        NoteInfo detail = new NoteInfo();

        detail.setId(c.getLong(0));
        detail.setNoteId(c.getString(1));
        detail.setNoteBookId(c.getString(2));
        detail.setUserId(c.getString(3));
        detail.setTitle(c.getString(4));
        detail.setTags(c.getString(5));
        detail.setContent(c.getString(6));
        detail.setIsMarkDown(c.getInt(7) != 0);
        detail.setIsPublicBlog(c.getInt(8) != 0);
        detail.setIsTrash(c.getInt(9) != 0);
        detail.setFileIds(c.getString(10));
        detail.setCreatedTime(c.getString(11));
        detail.setUpdatedTime(c.getString(12));
        detail.setPublicTime(c.getString(13));
        detail.setUsn(c.getInt(14));
        detail.setDesc(c.getString(15));
        detail.setNoteAbstract(c.getString(16));
        detail.setIsDirty(c.getInt(17) != 0);
        detail.setIsUploading(c.getInt(18) != 0);
        return detail;
    }

//    public NoteContent getNoteContentByNoteId(String noteId) {
//        String[] args = {String.valueOf(noteId)};
//        Cursor c = db.query(NOTE_CONTENT_TABLE, null, "noteId=?", args, null, null, "");
//        NoteContent content = new NoteContent();
//        if (c.moveToNext()) {
//            content.setNoteId(noteId);
//            content.setUserId(c.getString(1));
//            content.setNoteId(c.getString(2));
//        }
//        return content;
//    }

    private ContentValues getContentsFromMf(MediaFile mf) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_ID, mf.getId());
        values.put(COLUMN_NAME_NOTE_ID, mf.getNoteID());
        values.put(COLUMN_NAME_FILE_PATH, mf.getFilePath());
        values.put(COLUMN_NAME_FILE_NAME, mf.getFileName());
        values.put(COLUMN_NAME_TITLE, mf.getTitle());
        values.put(COLUMN_NAME_DESCRIPTION, mf.getDescription());
        values.put(COLUMN_NAME_CAPTION, mf.getCaption());
        values.put(COLUMN_NAME_HORIZONTAL_ALIGNMENT, mf.getHorizontalAlignment());
        values.put(COLUMN_NAME_WIDTH, mf.getWidth());
        values.put(COLUMN_NAME_HEIGHT, mf.getHeight());
        values.put(COLUMN_NAME_MIME_TYPE, mf.getMimeType());
//        values.put(COLUMN_NAME_FEATURED, mf.isFeatured());
//        values.put(COLUMN_NAME_IS_FEATURED_IN_POST, mf.isFeaturedInPost());
        values.put(COLUMN_NAME_FILE_URL, mf.getFileURL());
        values.put(COLUMN_NAME_THUMBNAIL_URL, mf.getThumbnailURL());
        values.put(COLUMN_NAME_MEDIA_ID, mf.getMediaId());

        //values.put(COLUMN_NAME_DATE_CREATED_GMT, mf.getDateCreatedGMT());

        if (mf.getUploadState() != null)
            values.put(COLUMN_NAME_UPLOAD_STATE, mf.getUploadState());
        else
            values.putNull(COLUMN_NAME_UPLOAD_STATE);

        return values;
    }

    public void saveMediaFile(MediaFile mf) {

        ContentValues values = getContentsFromMf(mf);

        int result = db.update(MEDIA_TABLE, values, "id=?",
                new String[]{mf.getId()});

        if (result == 0) {
            AppLog.i("insert new media:" + values);
            db.insert(MEDIA_TABLE, null, values);
        }

    }

    public void saveNoteContent(String noteId, String content) {
        if (TextUtils.isEmpty(noteId)) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put("content", content);

        db.update(NOTES_TABLE, values, "noteId=?", new String[]{noteId});

    }

    public long addNote(NoteInfo newNote) {
        ContentValues values = new ContentValues();
        values.put("noteId", newNote.getNoteId());
        values.put("notebookId", newNote.getNoteBookId());
        values.put("userId", newNote.getUserId());
        values.put("title", newNote.getTitle());
        values.put("updatedTime", newNote.getUpdatedTime());

        long result = db.insert(NOTES_TABLE, null, values);
        if (result > 0) {
            newNote.setId(result);
        }
        return result;
    }

    public void updateNote(NoteInfo note) {
        ContentValues values = getContentValuesFromNote(note);
        db.update(NOTES_TABLE, values, "id=?", new String[]{String.valueOf(note.getId())});
    }

    public void updateNoteByNoteId(NoteInfo note) {
        ContentValues values = getContentValuesFromNote(note);
        db.update(NOTES_TABLE, values, "noteId=?", new String[]{note.getNoteId()});
    }

    private ContentValues getContentValuesFromNote(NoteInfo note) {
        ContentValues values = new ContentValues();

        values.put("noteId", note.getNoteId());
        values.put("notebookId", note.getNoteBookId());
        values.put("userId", note.getUserId());
        values.put("title", note.getTitle());
        values.put("tags", note.getTags());
        values.put("content", note.getContent());
        values.put("isMarkDown", note.isMarkDown() ? 1 : 0);
        values.put("isBlog", note.isPublicBlog() ? 1 : 0);
        values.put("isTrash", note.isTrash() ? 1 : 0);
        values.put("files", note.getFileIds());
        values.put("createdTime", note.getCreatedTime());
        values.put("updatedTime", note.getUpdatedTime());
        values.put("publicTime", note.getPublicTime());
        values.put("usn", note.getUsn());
        values.put("desc", note.getDesc());
        values.put("note_abstract", note.getNoteAbstract());
        values.put("is_dirty", note.isDirty());
        values.put("isUploading", note.isUploading() ? 1 : 0);

        return values;
    }


    public NoteInfo getLocalNoteByNoteId(String noteId) {
        String[] args = {String.valueOf(noteId)};
        //Cursor c = db.query(NOTES_TABLE, null, null, null, null, null, "");
        Cursor c = db.query(NOTES_TABLE, null, "noteId=?", args, null, null, "");

        NoteInfo detail = null;
        try {
            if (c.moveToNext()) {
                detail = fillNote(c);
            }
            return detail;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public List<String> getNotebookTitles(String userId) {
        Cursor c = db.query(NOTEBOOKS_TABLE, null, "userId=?", new String[]{userId}, null, null, "");
        List<String> notebookTitles = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                notebookTitles.add(c.getString(4));
            }
            return notebookTitles;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public void deleteNote(long id) {
        String sql = "delete from notes where id='" + id + "'" ;
        db.execSQL(sql);
    }

    public void deleteNoteByNoteId(String noteId) {
        String sql = "delete from notes where noteId='" + noteId + "'" ;
        db.execSQL(sql);
    }


    public void deleteNoteInLocal(String noteId) {
        String sql = "update notes set isDeleted = 1 and is_dirty = 1 where noteId='" + noteId + "'" ;
        db.execSQL(sql);
    }

    public void deleteNotebook(String notebookId) {
        String sql = "delete from notebooks where notebookId='" + notebookId + "'" ;
        db.execSQL(sql);
    }

    public void deletenotebookInLocal(long id) {
        String sql = "delete from notebooks where id = " + id;
        db.execSQL(sql);
    }


    public List<String> getLocalNotebookIds(String userId) {
        Cursor c = db.query(NOTEBOOKS_TABLE, null, "userId=?", new String[]{userId}, null, null, "");
        List<String> notebookIds = new ArrayList<>();
        try {
            while (c.moveToNext()) {

                notebookIds.add(c.getString(1));
            }
            return notebookIds;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public void updateNotebook(NotebookInfo serverNotebook) {
        ContentValues values = new ContentValues();

        values.put("notebookId", serverNotebook.getNotebookId());
        values.put("parentNotebookId", serverNotebook.getParentNotebookId());
        values.put("userId", serverNotebook.getUserId());
        values.put("title", serverNotebook.getTitle());
        values.put("urlTitle", serverNotebook.getUrlTitle());
        values.put("isBlog", serverNotebook.isBlog() ? 1 : 0);
        values.put("isTrash", serverNotebook.isTrash() ? 1 : 0);
        values.put("isDeleted", serverNotebook.isDeleted() ? 1 : 0);
        values.put("updatedTime", serverNotebook.getUpdateTime());
        values.put("createdTime", serverNotebook.getCreateTime());
        values.put("usn", serverNotebook.getUsn());
        values.put("is_dirty", serverNotebook.isDirty() ? 1 : 0);

//        if (serverNotebook.getId() != 0l) {
//            db.update(NOTEBOOKS_TABLE, values, "id=?", new String[]{String.valueOf(serverNotebook.getId())});
//        } else {
//            db.update(NOTEBOOKS_TABLE, values, "notebookId=?", new String[]{serverNotebook.getNotebookId()});
//        }
        db.update(NOTEBOOKS_TABLE, values, "id=?", new String[]{String.valueOf(serverNotebook.getId())});

    }

    public NotebookInfo getLocalNotebookByNotebookId(String notebookId) {
        String[] args = {String.valueOf(notebookId)};
        //Cursor c = db.query(NOTES_TABLE, null, null, null, null, null, "");
        Cursor c = db.query(NOTEBOOKS_TABLE, null, "notebookId=?", args, null, null, "");

        NotebookInfo notebook = null;
        try {
            if (c.moveToNext()) {
                notebook = getNotebookFromCursor(c);
            }
            return notebook;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public void saveNotebooks(List<NotebookInfo> newNotebooks) {

        if (newNotebooks != null && newNotebooks.size() != 0) {
            db.beginTransaction();
            try {
                for (int i = 0; i < newNotebooks.size(); i++) {
                    ContentValues values = new ContentValues();
                    NotebookInfo notebook = (NotebookInfo) newNotebooks.get(i);

                    values.put("notebookId", notebook.getNotebookId());
                    values.put("parentNotebookId", notebook.getParentNotebookId());
                    values.put("userId", notebook.getUserId());
                    values.put("title", notebook.getTitle());
                    values.put("urlTitle", notebook.getUrlTitle());
                    values.put("isBlog", notebook.isBlog() ? 1 : 0);
                    values.put("isTrash", notebook.isTrash() ? 1 : 0);
                    values.put("title", notebook.getTitle());
                    values.put("updatedTime", notebook.getUpdateTime());
                    values.put("createdTime", notebook.getCreateTime());
                    values.put("usn", notebook.getUsn());

                    db.insert(NOTEBOOKS_TABLE, null, values);
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    public void updateUsn(String userId, int usn) {
        String sql = "update accounts set usn = " + usn + " where user_id = '" + userId + "'";
        db.execSQL(sql);
    }

    public void dangerouslyDeleteAllContent() {
//        db.execSQL("drop table " + NOTES_TABLE);
//        db.execSQL("drop table " + NOTEBOOKS_TABLE);
//        db.execSQL("drop table " + MEDIA_TABLE);

        db.delete(NOTES_TABLE, null, null);
        db.delete(NOTEBOOKS_TABLE, null, null);
        db.delete(MEDIA_TABLE, null, null);
    }

    public void publicNote(String noteId, boolean isPublic) {
        int publicNote = isPublic ? 1 : 0;
        String sql = "update notes set isBlog = " + publicNote + " where noteId = '" + noteId + "'";
        db.execSQL(sql);

    }

    public void updateMarkdown(boolean useMarkdown, String userId) {
        int mkd = useMarkdown ? 1 : 0;
        String sql = "update accounts set isMarkDown = " + mkd + " where local_id = 0 and user_id='" + userId + "'";
        db.execSQL(sql);

    }

    public List<String> getNoteisBlogIds() {
        String[] st = {"title"};
//        Cursor c = db.query(NOTES_TABLE, null, null , null, null, null, "");
        Cursor c = db.query(NOTES_TABLE, st , "isBlog=1", null, null, null, "");
        List<String> notebookIds = new ArrayList<>();
        try {
            while (c.moveToNext()) {

                notebookIds.add(c.getString(0));
            }
            return notebookIds;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public NoteDetailList getNoteisBlogList(String userId) {
        NoteDetailList listPosts = new NoteDetailList();

        String[] st = {"title"};
        //Cursor c = db.query(NOTES_TABLE, null, null, null, null, null, "");
        Cursor c = db.query(NOTES_TABLE, null, "isBlog=1 and userId=?", new String[]{userId}, null, null, "");
        try {
            while (c.moveToNext()) {
                String title = c.getString(4);
                String updateTime = c.getString(12);
                NoteInfo detail = new NoteInfo();

                detail.setId(c.getLong(0));
                detail.setNoteId(c.getString(1));
                detail.setTitle(title);
                detail.setContent(c.getString(6));
                //detail.setContent(getNoteContentByNoteId(c.getString(1)));
                detail.setUpdatedTime(updateTime);
                listPosts.add(detail);
            }
            return listPosts;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public List<NotebookInfo> getNotebookList(String userId) {
        Cursor c = db.query(NOTEBOOKS_TABLE, null, "userId=?", new String[]{userId}, null, null, "");
        List<NotebookInfo> notebooks = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                NotebookInfo notebook = getNotebookFromCursor(c);
                notebooks.add(notebook);
            }
            return notebooks;
        } finally {
            SqlUtils.closeCursor(c);
        }

    }

    private NotebookInfo getNotebookFromCursor(Cursor c) {
        NotebookInfo notebook = new NotebookInfo();
        notebook.setId(c.getLong(0));
        notebook.setNotebookId(c.getString(1));
        notebook.setParentNotebookId(c.getString(2));
        notebook.setUserId(c.getString(3));
        notebook.setTitle(c.getString(4));
        notebook.setUrlTitle(c.getString(5));
        notebook.setIsBlog(c.getInt(6) != 0);
        notebook.setIsTrash(c.getInt(7) != 0);
        notebook.setIsDeleted(c.getInt(8) != 0);
        notebook.setCreateTime(c.getString(9));
        notebook.setUpdateTime(c.getString(10));
        notebook.setUsn(c.getInt(11));
        notebook.setIsDirty(c.getInt(12) != 0);

        return notebook;
    }

    public void saveNoteSettings(NoteInfo note) {
        ContentValues values = new ContentValues();

        values.put("isBlog", note.isPublicBlog() ? 1 : 0);
        values.put("notebookId", note.getNoteBookId());
        values.put("tags", note.getTags());

        if (TextUtils.isEmpty(note.getNoteId())) {
            db.update(NOTES_TABLE, values, "id=?", new String[]{String.valueOf(note.getId())});
        } else {
            db.update(NOTES_TABLE, values, "noteId=?", new String[]{note.getNoteId()});
        }

    }


    public List<NoteInfo> getDirtyNotes() {
        String[] args = {"1"};
        //Cursor c = db.query(NOTES_TABLE, null, null, null, null, null, "");
        Cursor c = db.query(NOTES_TABLE, null, "is_dirty=?", args, null, null, "");
        List<NoteInfo> notes = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                NoteInfo note = fillNote(c);

                notes.add(note);
            }
            return notes;
        } finally {
            SqlUtils.closeCursor(c);
        }

    }

    public List<NotebookInfo> getDirtyNotebooks() {
        String[] args = {"1"};
        //Cursor c = db.query(NOTES_TABLE, null, null, null, null, null, "");
        Cursor c = db.query(NOTEBOOKS_TABLE, null, "is_dirty=?", args, null, null, "");
        List<NotebookInfo> notebooks = new ArrayList<>();
        try {
            while (c.moveToNext()) {
                NotebookInfo notebook = new NotebookInfo();
                notebook.setNotebookId(c.getString(1));
                notebook.setParentNotebookId(c.getString(2));
                notebook.setTitle(c.getString(4));
                notebook.setUrlTitle(c.getString(5));
                notebook.setIsBlog(c.getInt(6) == 0 ? false : true);
                notebook.setIsTrash(c.getInt(7) == 0 ? false : true);
                notebook.setIsDirty(c.getInt(8) == 0 ? false : true);
                notebook.setUpdateTime(c.getString(10));
                notebook.setUsn(c.getInt(11));
                notebooks.add(notebook);
            }
            return notebooks;
        } finally {
            SqlUtils.closeCursor(c);
        }

    }

    public long addNotebook(NotebookInfo newNotebook) {
        ContentValues values = new ContentValues();

        values.put("notebookId", newNotebook.getNotebookId());
        values.put("is_dirty", 0);
        values.put("userId", newNotebook.getUserId());
        long result = db.insert(NOTEBOOKS_TABLE, null, values);
        if (result > 0) {
            newNotebook.setId(result);
        }
        return result;
    }

    public NotebookInfo getLocalNotebookById(long localNotebookId) {
        String[] args = {String.valueOf(localNotebookId)};
        //Cursor c = db.query(NOTES_TABLE, null, null, null, null, null, "");
        Cursor c = db.query(NOTEBOOKS_TABLE, null, "id=?", args, null, null, "");

        NotebookInfo notebook = null;
        try {
            if (c.moveToNext()) {
                notebook = getNotebookFromCursor(c);
            }
            return notebook;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    public MediaFile getMediaFile(String imageUri) {
        Cursor c = db.query(MEDIA_TABLE, null, "fileURL=?",
                new String[]{imageUri}, null, null, null);

        try {
            if (c.moveToFirst()) {
                MediaFile mf = getMediaFileFromCursor(c);

                return mf;
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    private MediaFile getMediaFileFromCursor(Cursor c) {
        MediaFile mf = new MediaFile();

        mf.setId(c.getString(0));
        mf.setMediaId(c.getString(1));
        mf.setNoteID(c.getString(2));
        mf.setFilePath(c.getString(3));
        mf.setFileName(c.getString(4));
        mf.setTitle(c.getString(5));
        mf.setFileURL(c.getString(6));
        mf.setThumbnailURL(c.getString(7));
        mf.setUploadState(c.getString(8));
        mf.setDescription(c.getString(9));
        mf.setCaption(c.getString(10));
        mf.setHorizontalAlignment(c.getInt(11));
        mf.setWidth(c.getInt(12));
        mf.setHeight(c.getInt(13));
        mf.setMimeType(c.getString(14));
        return mf;
    }

    public MediaFile getMediaFileById(String id) {
        Cursor c = db.query(MEDIA_TABLE, null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null);

        try {
            if (c.moveToFirst()) {
                MediaFile mf = getMediaFileFromCursor(c);

                return mf;
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    public void updateDirtyUsn(String noteId, int usn) {
        ContentValues values = new ContentValues();
        values.put("usn", usn);
        values.put("is_dirty", 0);

        db.update(NOTES_TABLE, values, "noteId=?", new String[]{noteId});
    }


    public void updateAccountUsn(int serverUsn, String userId) {
        ContentValues values = new ContentValues();
        values.put("usn", serverUsn);

        db.update(ACCOUNTS_TABLE, values, "user_id=?", new String[]{userId});

    }

    public int getAccountUsn(String userId) {
        Cursor c = db.query(ACCOUNTS_TABLE, null, "user_id=?", new String[]{userId}, null, null, "");

        try {
            if (c.moveToNext()) {
                return c.getInt(8);
            }

        } finally {
            SqlUtils.closeCursor(c);
        }
        return 0;
    }

    public MediaFile getMediaFileByUrl(String url) {

        Cursor c = db.query(MEDIA_TABLE, null, "fileURL=?",
                new String[]{url}, null, null, null);

        try {
            if (c.moveToNext()) {
                return getMediaFileFromCursor(c);
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    public void deleteMediaFileByNoteId(String noteId) {
        String sql = "delete from media where noteId='" + noteId + "'";
        db.execSQL(sql);
    }

    public void updateMedia(String localFileId, String serverFileId) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME_MEDIA_ID, serverFileId);

        db.update(MEDIA_TABLE, values, "id=?", new String[]{localFileId});
    }

    public MediaFile getMediaFileByFileId(String fileId) {
        Cursor c = db.query(MEDIA_TABLE, null, "id=? or mediaId=?",
                new String[]{fileId, fileId}, null, null, null);

        try {
            if (c.moveToNext()) {
                return getMediaFileFromCursor(c);
            } else {
                return null;
            }
        } finally {
            c.close();
        }
    }

    public NoteDetailList getNotesListInNotebook(Long localNotebookId, String userId) {
        NoteDetailList notelist = new NoteDetailList();

        String notebookId = getNotebookIdByLocalId(localNotebookId);
        String[] args = {String.valueOf(notebookId), userId};
        if (localNotebookId == null) {
            return notelist;
        }

        //Cursor c = db.query(NOTES_TABLE, null, null, null, null, null, "");
        Cursor c = db.query(NOTES_TABLE, null, "notebookId=? and userId=?", args, null, null, "");
        try {
            while (c.moveToNext()) {
                NoteInfo detail = fillNote(c);
                notelist.add(detail);
            }
            return notelist;
        } finally {
            SqlUtils.closeCursor(c);
        }
    }

    private String getNotebookIdByLocalId(Long localNotebookId) {
        String[] args = {String.valueOf(localNotebookId)};
        Cursor c = db.query(NOTEBOOKS_TABLE, null, "id=?", args, null, null, "");
        try {
            if (c.moveToNext()) {
                return c.getString(1);
            }
        } finally {
            SqlUtils.closeCursor(c);
        }
        return null;
    }

}
