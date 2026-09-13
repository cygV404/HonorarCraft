package de.v404.honorarcraft.shared.data

import android.content.Context
import androidx.room.Room

/**
 * Android-Seite der Datenbank. Bewusst **ohne** `setDriver`: damit bleibt es beim
 * Android-eigenen SQLite, also genau bei dem Verhalten, das die ausgelieferte App
 * heute hat. Der Desktop nutzt dagegen das gebündelte SQLite (siehe jvmMain).
 */
object AndroidDatabase {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder<AppDatabase>(
                context = context.applicationContext,
                name = context.getDatabasePath(DATABASE_FILE_NAME).absolutePath,
            )
                // Kein fallbackToDestructiveMigration: ein fehlender Migrationspfad
                // muss beim Start auffallen, statt still die Rechnungen zu löschen.
                .addMigrations(*ALL_MIGRATIONS)
                .build()
            INSTANCE = instance
            instance
        }
    }

    /**
     * Schliesst die Datenbank und gibt die Instanz frei.
     *
     * Wird vom Import in `Backup` gebraucht: solange die Verbindung offen ist, darf die
     * Datei darunter nicht ausgetauscht werden. Nach dem Aufruf muss die App neu
     * gestartet werden, weil die bestehenden Flows an der geschlossenen Verbindung hängen.
     */
    fun closeInstance() {
        synchronized(this) {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
