package com.example;

import java.util.List;
import java.util.Vector;

public class LegacyProcessor {

    private Vector items = new Vector();

    public void addItem(Object item) {
        items.add(item);
    }

    public List getItems() {
        return items;
    }

    public int count() {
        return items.size();
    }
}
