package com.example.data.db

import android.util.Log
import com.example.BuildConfig
import com.example.ui.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

object NeonDatabaseHelper {
    private const val TAG = "NeonDatabaseHelper"

    // Default fallback values in case BuildConfig fields are missing or empty
    private const val DEFAULT_DB_URL = "postgresql://neondb_owner:npg_cprHoA5wBt0Z@ep-lively-surf-apnkb5f1-pooler.c-7.us-east-1.aws.neon.tech/neondb?sslmode=require&channel_binding=require"

    private fun getDbUrl(): String {
        return try {
            // Read VITE_DATABASE_URL from BuildConfig
            val url = BuildConfig.VITE_DATABASE_URL
            if (url.isNullOrBlank() || url == "MY_DATABASE_URL") {
                DEFAULT_DB_URL
            } else {
                url
            }
        } catch (e: Throwable) {
            DEFAULT_DB_URL
        }
    }

    private fun getConnection(): Connection? {
        val rawUrl = getDbUrl()
        try {
            Class.forName("org.postgresql.Driver")
            
            // Parse postgresql://user:password@host:port/dbname
            val cleanUrl = rawUrl.replace("postgresql://", "").replace("jdbc:postgresql://", "")
            val parts = cleanUrl.split("@")
            if (parts.size != 2) {
                Log.e(TAG, "Invalid database URL format")
                return null
            }
            
            val credentials = parts[0].split(":")
            if (credentials.size != 2) {
                Log.e(TAG, "Invalid credentials in URL")
                return null
            }
            val user = credentials[0]
            val password = credentials[1]
            
            val hostAndDb = parts[1].split("/")
            if (hostAndDb.size < 2) {
                Log.e(TAG, "Invalid host and database in URL")
                return null
            }
            val hostWithParams = hostAndDb[0]
            val dbNameWithParams = hostAndDb[1]
            val dbName = dbNameWithParams.split("?")[0]
            
            val jdbcUrl = "jdbc:postgresql://$hostWithParams/$dbName"
            
            val props = Properties()
            props.setProperty("user", user)
            props.setProperty("password", password)
            props.setProperty("ssl", "true")
            props.setProperty("sslmode", "require")
            
            return DriverManager.getConnection(jdbcUrl, props)
        } catch (e: Throwable) {
            Log.e(TAG, "Error establishing connection: ${e.message}", e)
            return null
        }
    }

    private fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            password
        }
    }

    suspend fun initDatabase(): Boolean = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        try {
            conn = getConnection()
            if (conn == null) {
                Log.e(TAG, "Failed to connect to Neon database during init")
                return@withContext false
            }
            val sql = """
                CREATE TABLE IF NOT EXISTS users (
                    username VARCHAR(100) PRIMARY KEY,
                    email VARCHAR(255) UNIQUE NOT NULL,
                    password VARCHAR(255) NOT NULL,
                    avatar_url VARCHAR(500),
                    banner_url VARCHAR(500),
                    level INT DEFAULT 1,
                    xp INT DEFAULT 25,
                    xp_needed INT DEFAULT 100,
                    watch_time INT DEFAULT 0
                )
            """.trimIndent()
            conn.createStatement().use { stmt ->
                stmt.execute(sql)
            }
            Log.d(TAG, "Database initialized successfully")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing database: ${e.message}", e)
            false
        } finally {
            try { conn?.close() } catch (ignored: Throwable) {}
        }
    }

    suspend fun registerUser(email: String, username: String, pass: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim()
        val cleanEmail = email.trim()
        if (cleanUser.length < 3) return@withContext false to "Username must be at least 3 characters"
        if (pass.length < 4) return@withContext false to "Password must be at least 4 characters"
        
        var conn: Connection? = null
        try {
            conn = getConnection()
            if (conn == null) {
                return@withContext false to "Unable to connect to the cloud database"
            }
            
            // Check if user already exists
            val checkSql = "SELECT username, email FROM users WHERE LOWER(username) = ? OR LOWER(email) = ?"
            conn.prepareStatement(checkSql).use { checkStmt ->
                checkStmt.setString(1, cleanUser.lowercase())
                checkStmt.setString(2, cleanEmail.lowercase())
                checkStmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val existingUser = rs.getString("username")
                        val existingEmail = rs.getString("email")
                        if (existingUser.equals(cleanUser, ignoreCase = true)) {
                            return@withContext false to "Username is already taken!"
                        }
                        if (existingEmail.equals(cleanEmail, ignoreCase = true)) {
                            return@withContext false to "Email address is already registered!"
                        }
                    }
                }
            }
            
            val hashed = hashPassword(pass)
            val defaultAvatar = "https://api.dicebear.com/7.x/adventurer/svg?seed=$cleanUser"
            val insertSql = """
                INSERT INTO users (username, email, password, avatar_url, banner_url, level, xp, xp_needed, watch_time) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            conn.prepareStatement(insertSql).use { stmt ->
                stmt.setString(1, cleanUser)
                stmt.setString(2, cleanEmail)
                stmt.setString(3, hashed)
                stmt.setString(4, defaultAvatar)
                stmt.setString(5, "")
                stmt.setInt(6, 1)
                stmt.setInt(7, 25)
                stmt.setInt(8, 100)
                stmt.setInt(9, 0)
                stmt.executeUpdate()
            }
            
            true to "Registration successful"
        } catch (e: Throwable) {
            Log.e(TAG, "Error registering user: ${e.message}", e)
            val errMessage = e.localizedMessage ?: ""
            if (errMessage.contains("unique constraint", ignoreCase = true) || errMessage.contains("duplicate key", ignoreCase = true)) {
                if (errMessage.contains("email", ignoreCase = true)) {
                    false to "Email address is already registered!"
                } else {
                    false to "Username is already taken!"
                }
            } else {
                false to (e.localizedMessage ?: "Database error during registration")
            }
        } finally {
            try { conn?.close() } catch (ignored: Throwable) {}
        }
    }

    suspend fun loginUser(identifier: String, pass: String): Pair<User?, String> = withContext(Dispatchers.IO) {
        val cleanId = identifier.trim().lowercase()
        var conn: Connection? = null
        try {
            conn = getConnection()
            if (conn == null) {
                return@withContext null to "Unable to connect to the cloud database"
            }
            
            val sql = "SELECT * FROM users WHERE LOWER(username) = ? OR LOWER(email) = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, cleanId)
                stmt.setString(2, cleanId)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) {
                        return@withContext null to "Account not found. Please sign up first."
                    }
                    
                    val storedHash = rs.getString("password")
                    val inputHash = hashPassword(pass)
                    if (storedHash != inputHash) {
                        return@withContext null to "Incorrect password. Please try again."
                    }
                    
                    val user = User(
                        username = rs.getString("username"),
                        email = rs.getString("email"),
                        avatarUrl = rs.getString("avatar_url") ?: "https://api.dicebear.com/7.x/adventurer/svg?seed=${rs.getString("username")}",
                        bannerUrl = rs.getString("banner_url") ?: "",
                        level = rs.getInt("level"),
                        xp = rs.getInt("xp"),
                        xpNeeded = rs.getInt("xp_needed"),
                        totalWatchTime = rs.getInt("watch_time")
                    )
                    user to "Login successful"
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error logging in: ${e.message}", e)
            null to (e.localizedMessage ?: "Database error during login")
        } finally {
            try { conn?.close() } catch (ignored: Throwable) {}
        }
    }

    suspend fun updateProfileInDb(username: String, avatarUrl: String, bannerUrl: String): Boolean = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        try {
            conn = getConnection()
            if (conn == null) return@withContext false
            
            val sql = "UPDATE users SET avatar_url = ?, banner_url = ? WHERE LOWER(username) = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, avatarUrl)
                stmt.setString(2, bannerUrl)
                stmt.setString(3, username.lowercase())
                stmt.executeUpdate()
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error updating profile in DB: ${e.message}", e)
            false
        } finally {
            try { conn?.close() } catch (ignored: Throwable) {}
        }
    }

    suspend fun saveUserStatsInDb(username: String, level: Int, xp: Int, xpNeeded: Int, watchTime: Int): Boolean = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        try {
            conn = getConnection()
            if (conn == null) return@withContext false
            
            val sql = "UPDATE users SET level = ?, xp = ?, xp_needed = ?, watch_time = ? WHERE LOWER(username) = ?"
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, level)
                stmt.setInt(2, xp)
                stmt.setInt(3, xpNeeded)
                stmt.setInt(4, watchTime)
                stmt.setString(5, username.lowercase())
                stmt.executeUpdate()
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Error saving user stats in DB: ${e.message}", e)
            false
        } finally {
            try { conn?.close() } catch (ignored: Throwable) {}
        }
    }
}
