package com.example.wowagoodsproject.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1.1~2.0(2026-06-08~09 빌드)은 가격을 숫자 칸(INTEGER)으로 만들었고, 그 다음 빌드부터 DB 버전을 올리지 않은 채
 * 글자 칸(TEXT)으로 바꿨다. 그때 DB 를 그대로 가진 채 올리면 Room 이 칸 형식이 다르다며 앱을 켜자마자 끈다.
 * 숫자 칸이면 표를 새로 만들어 글자 칸으로 옮긴다. 0 은 가격을 안 적은 것이라 빈 값으로 바꾼다.
 * 이미 글자 칸이면 아무것도 하지 않는다.
 */
internal fun SupportSQLiteDatabase.convertIntegerColumnToText(table: String, column: String) {
    data class Col(val name: String, val type: String, val notNull: Boolean, val default: String?, val pk: Boolean)

    val cols = query("PRAGMA table_info(`$table`)").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(Col(
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    type = c.getString(c.getColumnIndexOrThrow("type")).uppercase(),
                    notNull = c.getInt(c.getColumnIndexOrThrow("notnull")) == 1,
                    default = c.getString(c.getColumnIndexOrThrow("dflt_value")),
                    pk = c.getInt(c.getColumnIndexOrThrow("pk")) > 0
                ))
            }
        }
    }
    if (cols.firstOrNull { it.name == column }?.type != "INTEGER") return

    val definitions = cols.joinToString(", ") { col ->
        when {
            col.pk -> "`${col.name}` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"
            else -> buildString {
                append("`${col.name}` ").append(if (col.name == column) "TEXT" else col.type)
                if (col.notNull) append(" NOT NULL")
                if (col.name != column && col.default != null) append(" DEFAULT ${col.default}")
            }
        }
    }
    val names = cols.joinToString(", ") { "`${it.name}`" }
    val values = cols.joinToString(", ") {
        if (it.name == column) "CASE WHEN `$column` = 0 THEN '' ELSE CAST(`$column` AS TEXT) END" else "`${it.name}`"
    }
    execSQL("CREATE TABLE `${table}_text` ($definitions)")
    execSQL("INSERT INTO `${table}_text` ($names) SELECT $values FROM `$table`")
    execSQL("DROP TABLE `$table`")
    execSQL("ALTER TABLE `${table}_text` RENAME TO `$table`")
}
