package com.easyclipboard.app.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;

/**
 * Parcelable list of {@link Clip}. Ported from the original
 * com.dhm47.nativeclipboard.ClipList so clip collections can ride on Intents.
 */
public class ClipList extends ArrayList<Clip> implements Parcelable {
    private static final long serialVersionUID = -7943297501624787461L;

    public static final Creator<ClipList> CREATOR = new Creator<ClipList>() {
        @Override
        public ClipList createFromParcel(Parcel in) {
            return new ClipList(in);
        }

        @Override
        public ClipList[] newArray(int size) {
            return new ClipList[size];
        }
    };

    public ClipList() {
    }

    public ClipList(Parcel in) {
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        clear();
        int size = in.readInt();
        for (int i = 0; i < size; i++) {
            Clip c = (Clip) in.readSerializable();
            add(c);
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        int size = size();
        dest.writeInt(size);
        for (int i = 0; i < size; i++) {
            dest.writeSerializable(get(i));
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
