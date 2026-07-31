package com.xrdesk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

class IconComparisonActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comparison)

        findViewById<MaterialToolbar>(R.id.comparisonToolbar).setNavigationOnClickListener {
            finish()
        }
    }
}
