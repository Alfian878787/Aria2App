package com.gianlu.aria2app.Activities.MoreAboutDownload.ServersFragment;

class HeaderItem extends Item {
    private final int index;

    HeaderItem(int index) {
        this.index = index;
    }

    public String getTitle() {
        return "Index " + index;
    }

    @Override
    public int getItemType() {
        return Item.HEADER;
    }
}
