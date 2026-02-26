package com.mssh.data.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

object DatabaseFactory {
    fun create(context: Context): MsshDatabase {
        val driver = AndroidSqliteDriver(
            schema = MsshDatabase.Schema,
            context = context,
            name = "mssh.db"
        )
        return MsshDatabase(driver)
    }
}
