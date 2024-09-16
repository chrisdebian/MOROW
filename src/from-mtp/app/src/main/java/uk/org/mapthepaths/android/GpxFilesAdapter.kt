package uk.org.mapthepaths.android

import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class GpxFilesAdapter(val clickHandler: (String)->Unit): RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val files : List<String>

    init {
        val dir = File("${Environment.getExternalStorageDirectory().absolutePath}/mapthepaths")
        files = dir.listFiles { name -> name.name.endsWith(".gpx") }?.map { it.name } ?: arrayListOf()
    }

    inner class MyViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val textView : TextView = view.findViewById(android.R.id.text1)
        init {
            textView.setOnClickListener {
                clickHandler(textView.text.toString())
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return MyViewHolder(inflater.inflate(android.R.layout.simple_list_item_1, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as MyViewHolder).textView.text = files[position]
    }

    override fun getItemCount(): Int {
        return files.size
    }
}