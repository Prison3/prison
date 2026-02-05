package com.android.prison.entity;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

/**
 * Created by Prisoning on 3/8/22.
 **/
public class PLocationConfig implements Parcelable {

    public int pattern;
    public PCell cell;
    public List<PCell> allCell;
    public List<PCell> neighboringCellInfo;
    public PLocation location;

    @Override
    public int describeContents() {
        return 0;
    }

    public PLocationConfig() {
    }

    public PLocationConfig(Parcel in) {
        refresh(in);
    }

    public void refresh(Parcel in) {
        this.pattern = in.readInt();
        this.cell = in.readParcelable(PCell.class.getClassLoader());
        this.allCell = in.createTypedArrayList(PCell.CREATOR);
        this.neighboringCellInfo = in.createTypedArrayList(PCell.CREATOR);
        this.location = in.readParcelable(PLocation.class.getClassLoader());
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.pattern);
        dest.writeParcelable(this.cell, flags);
        dest.writeTypedList(this.allCell);
        dest.writeTypedList(this.neighboringCellInfo);
        dest.writeParcelable(this.location, flags);
    }

    public static final Creator<PLocationConfig> CREATOR = new Creator<PLocationConfig>() {
        @Override
        public PLocationConfig createFromParcel(Parcel source) {
            return new PLocationConfig(source);
        }

        @Override
        public PLocationConfig[] newArray(int size) {
            return new PLocationConfig[size];
        }
    };
}
