package com.example.timer

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.timer.ui.theme.TimerTheme
import javax.inject.Inject


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TimerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    TimerTheme {
        Greeting("Android")
    }
}

interface Authenticator {
    //자동로그인 가능한지 검사
    suspend fun canAutoSignIn(): Boolean
    // 로그인하기
    suspend fun signUpWithId(id: String, password: String)
    suspend fun signInWithId(id: String, password: String): Boolean
    // 로그아웃 하기
    suspend fun signOut()
}


class DataStorePreferencesAuthenticator @Inject constructor(context: Context, private val validator: IdValidator) :
    Authenticator {
    private val dataStore = context.createDataStore("DataStorePreferencesAuthenticator")

    override suspend fun canAutoSignIn() = runCatching {
        val pref = dataStore.data.first()
        pref[AUTO_SIGNIN_KEY] == true
    }.getOrDefault(false)

    override suspend fun signUpWithId(id: String, password: String) {
        Log.d("signUpWithId","$id $password")
        dataStore.edit { pref ->
            pref[ID_KEY] = id
            pref[PW_KEY] = password
        }
    }

    override suspend fun signInWithId(id: String, password: String): Boolean {
        return runCatching {
            val pref = dataStore.data.first()
            validator.validateIdAndPwWithOthers(id, password, pref[ID_KEY], pref[PW_KEY]).also {
                if (it) {
                    dataStore.edit { pref ->
                        pref[AUTO_SIGNIN_KEY] = true
                    }
                }
            }
        }.getOrDefault(false)
    }

    override suspend fun signOut() {
        dataStore.edit { pref ->
            pref.remove(AUTO_SIGNIN_KEY)
        }
    }

    companion object {
        private val ID_KEY = preferencesKey<String>("id")
        private val PW_KEY = preferencesKey<String>("pw")
        private val AUTO_SIGNIN_KEY = preferencesKey<Boolean>("autoSignIn")
    }
}

//EncryptedFileAuthenticator.kt
class EncryptedFileAuthenticator @Inject constructor(context: Context, private val validator: IdValidator) :
    Authenticator {
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val file = File(context.getExternalFilesDir(null), "data.txt")
    private val encryptedFile = EncryptedFile.Builder(
        context, file, masterKey, AES256_GCM_HKDF_4KB
    ).build()

    override suspend fun canAutoSignIn(): Boolean = withContext(Dispatchers.IO) {
        file.exists()
    }

    override suspend fun signUpWithId(id: String, password: String) {
        encryptedFile.openFileOutput().use {
            it.write("$id\n$password\n".toByteArray())
        }
    }

    override suspend fun signInWithId(id: String, password: String): Boolean = withContext(Dispatchers.IO) {
        createFileIfNotExist()
        val content = encryptedFile.openFileInput().bufferedReader().useLines {
            it.fold("") { acc, line -> acc + "$line\n" }
        }
        val lastId = content.split("\n").firstOrNull()
        val lastPw = content.split("\n")[1]

        validator.validateIdAndPwWithOthers(id, password, lastId, lastPw)
    }

    private fun createFileIfNotExist() {
        if (!file.exists()) file.createNewFile()
    }

    override suspend fun signOut() {
        deleteFile()
    }

    private suspend fun deleteFile() = withContext(Dispatchers.IO) {
        file.delete()
    }
}

//EncryptedSharedPreferencesAuthenticator.kt
class EncryptedSharedPreferencesAuthenticator
@Inject constructor(context: Context, @Named("SharedPreferences") authenticator: Authenticator) :
    Authenticator by ((authenticator as? SharedPreferencesAuthenticator
        ?: throw TypeCastException("Authenticator -> SharedPreferencesAuthenticator failed")).apply {

        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

        val encryptedSharedPreferences =
            EncryptedSharedPreferences.create(context, "EncryptedSharedPreferences", masterKey, AES256_SIV, AES256_GCM)

        replaceSharedPreferences(encryptedSharedPreferences)
    })


//Invalidator
class IdValidator {
    fun validateIdAndPwWithOthers(id: String, pw: String, lastId: String?, lastPw: String?): Boolean {
        return id.isNotBlank() && pw.isNotBlank() && id == lastId && pw == lastPw
    }
}

//SharedPreferencesAuthenticator.kt
class SharedPreferencesAuthenticator @Inject constructor(
    context: Context, private val validator: IdValidator
) : Authenticator {
    private var sharedPreferences = context.getSharedPreferences("sharedPreferences", 0)

    fun replaceSharedPreferences(sharedPreferences: SharedPreferences) {
        this.sharedPreferences = sharedPreferences
    }

    override suspend fun canAutoSignIn() = sharedPreferences.getBoolean(AUTO_SIGNIN_KEY, false)

    override suspend fun signUpWithId(id: String, password: String) = sharedPreferences.edit(true) {
        putString(ID_KEY, id)
        putString(PW_KEY, password)
    }

    override suspend fun signInWithId(id: String, password: String) = validator.validateIdAndPwWithOthers(
        id, password, sharedPreferences.getString(ID_KEY, ""), sharedPreferences.getString(PW_KEY, "")
    ).also {
        if (it) {
            sharedPreferences.edit(true) {
                putBoolean(AUTO_SIGNIN_KEY, true)
            }
        }
    }

    override suspend fun signOut() {
        sharedPreferences.edit(true) {
            remove(AUTO_SIGNIN_KEY)
        }
    }

    companion object {
        private const val ID_KEY = "ID"
        private const val PW_KEY = "PW"
        private const val AUTO_SIGNIN_KEY = "AUTO_SIGNIN"
    }
}