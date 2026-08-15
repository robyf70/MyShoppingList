package it.robertofichera.myshoppinglist.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ShoppingDao {

    @Transaction
    @Query("SELECT * FROM shopping_lists ORDER BY createdAt DESC")
    fun observeLists(): Flow<List<ListWithItems>>

    @Transaction
    @Query("SELECT * FROM shopping_lists WHERE id = :listId")
    fun observeList(listId: Long): Flow<ListWithItems?>

    @Insert
    suspend fun insertList(list: ShoppingList): Long

    @Update
    suspend fun updateList(list: ShoppingList)

    @Delete
    suspend fun deleteList(list: ShoppingList)

    @Insert
    suspend fun insertItem(item: Item)

    @Update
    suspend fun updateItem(item: Item)

    @Delete
    suspend fun deleteItem(item: Item)

    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeProducts(): Flow<List<Product>>

    @Query(
        """SELECT p.*, (SELECT COUNT(*) FROM items WHERE productId = p.id) AS usageCount
           FROM products p ORDER BY p.name COLLATE NOCASE"""
    )
    fun observeProductsWithUsage(): Flow<List<ProductWithUsage>>

    /** NOCASE so typing "milk" reuses an existing "Milk" instead of creating a twin. */
    @Query("SELECT * FROM products WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findProductByName(name: String): Product?

    @Insert
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("UPDATE products SET defaultPriceCents = :priceCents WHERE id = :productId")
    suspend fun updateProductPrice(productId: Long, priceCents: Long)
}
