package com.leanote.android.model;

import java.util.ArrayList;

/**
 * Created by binnchx on 10/18/15.
 */
public class NoteDetailList extends ArrayList<NoteInfo> {

    public boolean isSameList(NoteDetailList noteList) {
        if (noteList == null || this.size() != noteList.size()) {
            return false;
        }

        for (int i = 0; i < noteList.size(); i++) {
            NoteInfo newNote = noteList.get(i);
            NoteInfo currentNote = this.get(i);

            if (newNote.getNoteId() != currentNote.getNoteId())
                return false;
            if (!newNote.getTitle().equals(currentNote.getTitle()))
                return false;
        }

        return true;
    }

    public int indexOfPost(NoteInfo note) {
        if (note == null) {
            return -1;
        }

        for (int i = 0; i < size(); i++) {
            if (this.get(i).getId().longValue() == note.getId().longValue()) {
                return i;
            }
        }
        return -1;
    }



}
