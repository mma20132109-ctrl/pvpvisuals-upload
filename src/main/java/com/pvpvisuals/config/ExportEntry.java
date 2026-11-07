package com.pvpvisuals.config;

/** Строка списка экспортированных конфигов. {@code name} — имя файла без .json, {@code when} — готовая подпись даты. */
public record ExportEntry(String name, long modified, long size, String when) { }
