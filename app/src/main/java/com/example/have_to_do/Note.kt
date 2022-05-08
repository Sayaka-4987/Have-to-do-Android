package com.example.have_to_do

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import android.util.Log
import kotlin.math.abs

// 把 magic word 变成有命名的全局常量
object EditMode {
    const val INSERT = -1   // 表示当前编辑窗口用于新增便签
}

object SelectResult {
    const val NOT_FOUND: Long = -1  // 表示数据库中未找到该标签
}

// 数据类 Note，包含便签信息。其中 id 项在插入数据库时才更改为主键值
class Note (var title: String, var content: String, var importance: Int, var id: Long) {
    // 次级构造函数
    constructor(title: String, content: String, importance: Int) : this(title, content, importance, -1) {}
    constructor(title: String, content: String) : this(title, content, 0, -1) {}
    constructor(title: String, importance: Int) : this(title, "", importance, -1) {}
    constructor(title: String) : this(title, "", 0, -1) {}

    // init{} 代码段中内容将在在主构造函数后执行，此处确保 importance 是属于 [0, 4] 范围的正整数
    init {
        importance = abs((importance) % 5)
    }
}

// 全局常量，记录数据库表中字段名
object FeedEntry : BaseColumns {
    const val TABLE_NAME = "note"                       // 表名
    const val COLUMN_NAME_TITLE = "title"               // 标题列
    const val COLUMN_NAME_CONTENT = "content"           // 内容列
    const val COLUMN_NAME_IMPORTANCE = "importance"     // 重要等级列
}

// 建表 SQL 语句
private const val SQL_CREATE_ENTRIES =
    "CREATE TABLE " +
    "${FeedEntry.TABLE_NAME} (" +
    "${BaseColumns._ID} INTEGER PRIMARY KEY," +         // 自增ID主键
    "${FeedEntry.COLUMN_NAME_TITLE} TEXT UNIQUE," +     // 标题，唯一索引
    "${FeedEntry.COLUMN_NAME_CONTENT} TEXT," +          // 内容
    "${FeedEntry.COLUMN_NAME_IMPORTANCE} INTEGER)"      // 重要等级

// 删除表 SQL 语句
private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${FeedEntry.TABLE_NAME}"

// 继承 SQLiteHelper 类
class NoteDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    companion object {
        const val DATABASE_VERSION = 1          // 数据库版本
        const val DATABASE_NAME = "Note.db"     // 文件名
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }
}

// 查询数据库内的所有便签，加载到内存
fun queryAllNote(dbHelper: NoteDbHelper): MutableList<Note> {
    val db = dbHelper.readableDatabase
    val sortOrder = "${FeedEntry.COLUMN_NAME_IMPORTANCE} DESC"
    val cursor = db.query(
        FeedEntry.TABLE_NAME,    // FROM Note
        null,           // SELECT *
        null,
        null,
        null,
        null,
        sortOrder   // 按重要程度降序排列
    )

    var notes = mutableListOf<Note>()
    with(cursor) {
        while (moveToNext()) {
            // 读取游标内容
            val itemID = getLong(getColumnIndexOrThrow(BaseColumns._ID))
            val itemTitle = getString(getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_TITLE))
            val itemContent = getString(getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_CONTENT))
            val itemImportance = getInt(getColumnIndexOrThrow(FeedEntry.COLUMN_NAME_IMPORTANCE))
            val note = Note(itemTitle, itemContent, itemImportance, itemID)
            notes.add(note)
        }
    }
    cursor.close()  // 关闭游标
    return notes
}

// 获取数据的主键 ID
fun getID(dbHelper: NoteDbHelper, title: String): Long {
    val db = dbHelper.readableDatabase
    val projection = arrayOf(BaseColumns._ID, FeedEntry.COLUMN_NAME_TITLE)
    val selection = "${FeedEntry.COLUMN_NAME_TITLE} = ?"
    val selectionArgs = arrayOf(title)

    val cursor = db.query(
        FeedEntry.TABLE_NAME,   // The table to query
        projection,             // The array of columns to return (pass null to get all)
        selection,              // The columns for the WHERE clause
        selectionArgs,          // The values for the WHERE clause
        null,                   // don't group the rows
        null,                   // don't filter by row groups
        null
    )

    var itemId: Long = SelectResult.NOT_FOUND
    with(cursor) {
        while (moveToNext()) {
            itemId = getLong(getColumnIndexOrThrow(BaseColumns._ID))
        }
    }
    cursor.close()
    return itemId
}

// 新增便签
fun insertNote(dbHelper: NoteDbHelper, note: Note) {
    // Gets the data repository in write mode
    val db = dbHelper.writableDatabase

    // Create a new map of values, where column names are the keys
    val values = ContentValues().apply {
        put(FeedEntry.COLUMN_NAME_TITLE, note.title)
        put(FeedEntry.COLUMN_NAME_CONTENT, note.content)
        put(FeedEntry.COLUMN_NAME_IMPORTANCE, note.importance)
    }

    if (note.id == SelectResult.NOT_FOUND) {
        // 新增过去不存在的便签
        val newRowId = db.insert(FeedEntry.TABLE_NAME, null, values)
        note.id = newRowId
        Log.d("insert id = ${note.id}", "title = ${note.title}")
    } else {
        // 更新已有便签
        updateNote(dbHelper, note)
        Log.d("update id = ${note.id}", "title = ${note.title}")
    }
}

// 删除指定的便签
fun deleteNote(dbHelper: NoteDbHelper, id: Long) {
    Log.d("delete id = ", id.toString())
    val db = dbHelper.writableDatabase
    val selection = "${BaseColumns._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    val deletedRows = db.delete(FeedEntry.TABLE_NAME, selection, selectionArgs)
}

// 更新指定便签内容
fun updateNote(dbHelper: NoteDbHelper, note: Note) {
    Log.d("update id = ", note.id.toString())
    val db = dbHelper.writableDatabase
    val values = ContentValues().apply {
        put(FeedEntry.COLUMN_NAME_TITLE, note.title)
        put(FeedEntry.COLUMN_NAME_CONTENT, note.content)
        put(FeedEntry.COLUMN_NAME_IMPORTANCE, note.importance)
    }
    val selection = "${BaseColumns._ID} = ?"
    val selectionArgs = arrayOf(note.id.toString())
    val count = db.update(FeedEntry.TABLE_NAME, values, selection, selectionArgs)
}

/*
此前试图使用 Room 的废弃代码
// 数据访问对象 DAO 类
@Dao
interface NoteDao {
    @Insert
    fun insertAll(vararg Notes: Note)

    @Update
    fun updateAll(vararg Notes: Note)

    @Delete
    fun delete(Note: Note)

    @Query("SELECT * FROM Note")
    fun getAll(): LiveData<Array<Note>>

    @Query("SELECT * FROM Note WHERE uid IN (:NoteIds)")
    fun loadAllByIds(NoteIds: IntArray): Array<Note>
}

// 数据库配置类
@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class NoteRoomDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    companion object {
        @Volatile
        var INSTANCE: NoteRoomDatabase? = null
        fun getDatabase(context: Context): NoteRoomDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteRoomDatabase::class.java,
                    "note_database"
                ).build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }
}

class NoteRepository(application: Application) {
    private var noteDao: NoteDao
    init {
        val database = NoteRoomDatabase.getDatabase(application)
        noteDao = database.noteDao()
    }

    val readAllNote: LiveData<Array<Note>> = noteDao.getAll()
    suspend fun insertUser(note: Note) {
        noteDao.insertAll(note)
    }
}
*/