package uk.org.mapthepaths.android

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.gpx_files.*

class GpxFilesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Choose your GPX file"
        setContentView(R.layout.gpx_files)
        gpxList.adapter = GpxFilesAdapter {
            Intent().apply {
                putExtra("uk.org.mapthepaths.android.gpxFile", it)
                setResult(RESULT_OK, this)
                finish()
            }
        }
        val layoutManager = LinearLayoutManager(this)
        gpxList.layoutManager = layoutManager
        gpxList.addItemDecoration(DividerItemDecoration(this, layoutManager.orientation))
    }
}