package com.example.meditrack;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Medicine {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public String time;
    public String dosage;
    public String type;
    public String days; // e.g., "Every day" or "S, M, T, W, T, F, S"
    public int userId;
    public boolean isTaken;
    public String firestoreId;
    public int stock;
    public String imagePath;
}