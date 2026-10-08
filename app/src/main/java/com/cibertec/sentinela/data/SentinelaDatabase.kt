package com.cibertec.sentinela.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

// Equivalente al modelo Core Data NovaMovil.xcdatamodeld

@Entity(tableName = "contactos")
data class ContactoEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val id: String?,
    val nombre: String?,
    val telefono: String?
)

@Entity(tableName = "historial")
data class HistorialEntity(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val eventId: Long = 0,
    val fecha: Long? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val tipo: String? = null
)

@Dao
interface ContactoDao {

    @Query("SELECT * FROM contactos")
    suspend fun obtenerTodos(): List<ContactoEntity>

    @Query("SELECT COUNT(*) FROM contactos WHERE telefono = :telefono OR id = :id")
    suspend fun contar(telefono: String, id: String): Int

    @Insert
    suspend fun insertar(contacto: ContactoEntity)

    @Delete
    suspend fun eliminar(contacto: ContactoEntity)
}

@Dao
interface HistorialDao {

    @Insert
    suspend fun insertar(historial: HistorialEntity)

    @Query("SELECT * FROM historial ORDER BY fecha DESC")
    suspend fun obtenerTodos(): List<HistorialEntity>
}

/** Equivalente a CoreDataManager. */
@Database(entities = [ContactoEntity::class, HistorialEntity::class], version = 1, exportSchema = false)
abstract class SentinelaDatabase : RoomDatabase() {

    abstract fun contactoDao(): ContactoDao
    abstract fun historialDao(): HistorialDao

    companion object {
        @Volatile
        private var instancia: SentinelaDatabase? = null

        fun get(context: Context): SentinelaDatabase {
            return instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    SentinelaDatabase::class.java,
                    "Sentinela.db"
                ).build().also { instancia = it }
            }
        }
    }
}
