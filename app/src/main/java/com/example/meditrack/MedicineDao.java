package com.example.meditrack;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MedicineDao {

    @Insert
    long insert(Medicine medicine);

    @Insert
    void insertAll(List<Medicine> medicines);

    @androidx.room.Update
    void update(Medicine medicine);

    @androidx.room.Delete
    void delete(Medicine medicine);

    @Query("SELECT * FROM Medicine WHERE userId = :userId")
    List<Medicine> getByUserId(int userId);

    @Query("SELECT * FROM Medicine WHERE id = :id LIMIT 1")
    Medicine getById(int id);

    @Query("SELECT * FROM Medicine WHERE firestoreId = :firestoreId LIMIT 1")
    Medicine getByFirestoreId(String firestoreId);

    @Query("SELECT * FROM Medicine")
    List<Medicine> getAllMedicines();
}