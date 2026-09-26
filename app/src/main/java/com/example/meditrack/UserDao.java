package com.example.meditrack;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface UserDao {
    @Insert
    void register(User user);

    @Query("SELECT * FROM User WHERE username = :username AND password = :password LIMIT 1")
    User login(String username, String password);

    @Query("SELECT * FROM User WHERE username = :username LIMIT 1")
    User findByUsername(String username);

    @androidx.room.Update
    void update(User user);

    @Query("SELECT * FROM User WHERE role = 'Patient'")
    java.util.List<User> getAllPatients();
}