package com.example.twobuttons;

class CaseEntry {
    final String id;
    String type;
    String title;
    String number;

    CaseEntry(String id, String type, String title, String number) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.number = number;
    }

    @Override
    public String toString() {
        return title + "\n" + type + "  |  No. " + number;
    }
}
