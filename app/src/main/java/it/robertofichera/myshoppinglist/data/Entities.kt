package it.robertofichera.myshoppinglist.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

@Entity(tableName = "shopping_lists", indices = [Index(value = ["uuid"], unique = true)])
data class ShoppingList(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** 0 means no budget set. The SQL default lets migration 2→3 add the column in place. */
    @ColumnInfo(defaultValue = "0") val budgetCents: Long = 0,
    /** Identifies the list across devices, so a shared copy coming back updates it in place. */
    @ColumnInfo(defaultValue = "") val uuid: String = UUID.randomUUID().toString(),
    /** The card's background as ARGB; 0 leaves it to the theme. */
    @ColumnInfo(defaultValue = "0") val colorArgb: Int = 0,
)

/** A product the user can put on any list. [defaultPriceCents] is the last price they entered for it. */
@Entity(
    tableName = "products",
    indices = [Index(value = ["name"], unique = true), Index(value = ["barcode"])],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultPriceCents: Long = 0,
    /** The barcode last scanned for this product; null until one is. */
    val barcode: String? = null,
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingList::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("listId"), Index("productId")],
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val productId: Long,
    val quantity: Double = 1.0,
    val priceCents: Long = 0,
    val bought: Boolean = false,
)

/** Rounded once per line, receipt-style, so summing lines never drifts. */
val Item.lineTotalCents: Long get() = Math.round(quantity * priceCents)

data class ItemWithProduct(
    @Embedded val item: Item,
    @Relation(parentColumn = "productId", entityColumn = "id") val product: Product,
)

val ItemWithProduct.lineTotalCents: Long get() = item.lineTotalCents

data class ListWithItems(
    @Embedded val list: ShoppingList,
    @Relation(entity = Item::class, parentColumn = "id", entityColumn = "listId")
    val items: List<ItemWithProduct>,
)

val ListWithItems.totalCents: Long get() = items.sumOf { it.lineTotalCents }

/** [usageCount] is how many list items reference this product; it gates deletion. */
data class ProductWithUsage(
    @Embedded val product: Product,
    val usageCount: Int,
)
