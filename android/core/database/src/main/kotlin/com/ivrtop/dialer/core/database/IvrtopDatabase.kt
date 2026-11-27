package com.ivrtop.dialer.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        AccountEntity::class,
        CallLogEntity::class,
        FavoriteEntity::class,
        BlockRuleEntity::class,
        LocalContactEntity::class,
        PushSeenEntity::class,
        VoicemailEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class IvrtopDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun callLog(): CallLogDao
    abstract fun favorites(): FavoriteDao
    abstract fun blocks(): BlockDao
    abstract fun localContacts(): LocalContactDao
    abstract fun pushSeen(): PushSeenDao
    abstract fun voicemail(): VoicemailDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun db(@ApplicationContext context: Context): IvrtopDatabase =
        // Stored in the default databases dir which is excluded from backup via
        // res/xml/data_extraction_rules.xml (call history is personal data).
        Room.databaseBuilder(context, IvrtopDatabase::class.java, "ivrtop.db")
            .build()

    @Provides fun accounts(db: IvrtopDatabase) = db.accounts()
    @Provides fun callLog(db: IvrtopDatabase) = db.callLog()
    @Provides fun favorites(db: IvrtopDatabase) = db.favorites()
    @Provides fun blocks(db: IvrtopDatabase) = db.blocks()
    @Provides fun localContacts(db: IvrtopDatabase) = db.localContacts()
    @Provides fun pushSeen(db: IvrtopDatabase) = db.pushSeen()
    @Provides fun voicemail(db: IvrtopDatabase) = db.voicemail()
}
