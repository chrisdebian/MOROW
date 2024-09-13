package uk.org.mapthepaths.android

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import androidx.appcompat.app.AppCompatActivity


class Preferences: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction().replace(android.R.id.content, PrefFragment()).commit()
    }
}

class PrefFragment: PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)
    }
}
