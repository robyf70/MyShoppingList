package it.robertofichera.myshoppinglist.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ShoppingList::class, Product::class, Item::class], version = 4)
abstract class AppDatabase : RoomDatabase() {

    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shopping.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }

        /**
         * Items referenced a product by name; they now reference the new products table by id.
         * The catalog is seeded from the names already in use, so an upgrade starts populated.
         * SQLite cannot add a foreign key in place, so items is rebuilt rather than altered.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `products` (
                       `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                       `name` TEXT NOT NULL,
                       `defaultPriceCents` INTEGER NOT NULL)"""
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_name` ON `products` (`name`)")
                db.execSQL(
                    """INSERT INTO products (name, defaultPriceCents)
                       SELECT name, MAX(priceCents) FROM items GROUP BY name"""
                )

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `items_new` (
                       `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                       `listId` INTEGER NOT NULL,
                       `productId` INTEGER NOT NULL,
                       `quantity` REAL NOT NULL,
                       `priceCents` INTEGER NOT NULL,
                       `bought` INTEGER NOT NULL,
                       FOREIGN KEY(`listId`) REFERENCES `shopping_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
                       FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"""
                )
                db.execSQL(
                    """INSERT INTO items_new (id, listId, productId, quantity, priceCents, bought)
                       SELECT i.id, i.listId, p.id, i.quantity, i.priceCents, i.bought
                       FROM items i JOIN products p ON p.name = i.name"""
                )
                db.execSQL("DROP TABLE `items`")
                db.execSQL("ALTER TABLE `items_new` RENAME TO `items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_listId` ON `items` (`listId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_items_productId` ON `items` (`productId`)")
            }
        }

        /** Adds the optional per-list spending budget; 0 means none is set. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `shopping_lists` ADD COLUMN `budgetCents` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * Identifies each list across devices. Existing rows are filled in so a list created
         * before sharing existed can still be shared and recognised when it comes back.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `shopping_lists` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE `shopping_lists` SET `uuid` = lower(hex(randomblob(16)))")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_shopping_lists_uuid` ON `shopping_lists` (`uuid`)"
                )
            }
        }
    }
}
