package com.sentimentdashboard.sentiment_dashboard.service;

import java.util.ArrayList;
import java.util.List;

/** Small RFC-4180 style CSV parser: handles quotes, commas and newlines inside quotes, BOM. */
public final class CsvParser {

    private CsvParser() {}

    public static List<List<String>> parse(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) return rows;
        if (text.charAt(0) == '﻿') text = text.substring(1);

        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                addRow(rows, row);
                row = new ArrayList<>();
            } else {
                cell.append(c);
            }
        }
        row.add(cell.toString());
        addRow(rows, row);
        return rows;
    }

    private static void addRow(List<List<String>> rows, List<String> row) {
        boolean blank = row.stream().allMatch(s -> s.trim().isEmpty());
        if (!blank) rows.add(row);
    }
}