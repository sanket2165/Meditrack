package com.example.meditrack;

import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HistoryActivity extends AppCompatActivity {

    private FirebaseFirestore firestore;
    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        username = getIntent().getStringExtra("username");

        Toolbar toolbar = findViewById(R.id.toolbarHistory);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("History: " + username);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        firestore = FirebaseFirestore.getInstance();
        rvHistory = findViewById(R.id.rvHistory);
        rvHistory.setLayoutManager(new LinearLayoutManager(this));

        loadHistory();
    }

    private void loadHistory() {
        // First try by username
        firestore.collection("history")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (queryDocumentSnapshots.isEmpty()) {
                        // If no results, maybe try by userId? 
                        // For now just show empty toast or load whatever is found
                        Toast.makeText(this, "No history found for " + username, Toast.LENGTH_SHORT).show();
                    }
                    
                    List<Map<String, Object>> historyList = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        historyList.add(doc.getData());
                    }
                    
                    // Sort locally safely since index might not be ready
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        historyList.sort((o1, o2) -> {
                            Long t1 = getTimestampAsLong(o1.get("timestamp"));
                            Long t2 = getTimestampAsLong(o2.get("timestamp"));
                            return t2.compareTo(t1); // Descending
                        });
                    }

                    adapter = new HistoryAdapter(historyList);
                    rvHistory.setAdapter(adapter);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error loading history: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private Long getTimestampAsLong(Object obj) {
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Double) return ((Double) obj).longValue();
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof Timestamp) return ((Timestamp) obj).getSeconds() * 1000;
        return 0L;
    }
}
