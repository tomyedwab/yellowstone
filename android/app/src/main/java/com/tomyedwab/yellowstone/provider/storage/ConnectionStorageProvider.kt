package com.tomyedwab.yellowstone.provider.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tomyedwab.yellowstone.provider.connection.HubAccount
import com.tomyedwab.yellowstone.provider.connection.HubAccountList
import java.util.UUID

class ConnectionStorageProvider(private val context: Context) {
    
    companion object {
        private const val PREF_NAME = "yellowstone_secure_storage"
        private const val KEY_ACCOUNTS = "hub_accounts"
        private const val KEY_SELECTED_ACCOUNT = "selected_account_id"
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREF_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    private val gson = Gson()
    
    fun loadHubAccounts(): HubAccountList {
        val accountsJson = sharedPreferences.getString(KEY_ACCOUNTS, null)
        val selectedAccountId = sharedPreferences.getString(KEY_SELECTED_ACCOUNT, null)
        
        val accounts = if (accountsJson != null) {
            val type = object : TypeToken<List<HubAccount>>() {}.type
            gson.fromJson<List<HubAccount>>(accountsJson, type)
        } else {
            emptyList()
        }
        
        return HubAccountList(
            accounts = accounts,
            selectedAccount = selectedAccountId
        )
    }
    
    fun saveHubAccount(hubUrl: String, username: String, refreshToken: String): HubAccount {
        val currentAccountList = loadHubAccounts()
        
        val existingAccount = currentAccountList.accounts.find { account ->
            account.url == hubUrl && account.name == username
        }
        
        val account = if (existingAccount != null) {
            existingAccount.copy(refreshToken = refreshToken)
        } else {
            HubAccount(
                id = UUID.randomUUID().toString(),
                name = username,
                url = hubUrl,
                refreshToken = refreshToken
            )
        }
        
        val updatedAccounts = if (existingAccount != null) {
            currentAccountList.accounts.map { acc ->
                if (acc.id == existingAccount.id) account else acc
            }
        } else {
            currentAccountList.accounts + account
        }
        
        val updatedAccountList = HubAccountList(
            accounts = updatedAccounts,
            selectedAccount = account.id
        )
        
        saveHubAccountList(updatedAccountList)
        return account
    }
    
    fun clearSelectedAccount() {
        val currentAccountList = loadHubAccounts()
        val updatedAccountList = currentAccountList.copy(selectedAccount = null)
        saveHubAccountList(updatedAccountList)
    }
    
    fun setSelectedAccount(accountId: String) {
        val currentAccountList = loadHubAccounts()
        val accountExists = currentAccountList.accounts.any { it.id == accountId }
        
        if (accountExists) {
            val updatedAccountList = currentAccountList.copy(selectedAccount = accountId)
            saveHubAccountList(updatedAccountList)
        }
    }
    
    fun removeAccount(accountId: String) {
        val currentAccountList = loadHubAccounts()
        val updatedAccounts = currentAccountList.accounts.filter { it.id != accountId }
        
        val updatedSelectedAccount = if (currentAccountList.selectedAccount == accountId) {
            null
        } else {
            currentAccountList.selectedAccount
        }
        
        val updatedAccountList = HubAccountList(
            accounts = updatedAccounts,
            selectedAccount = updatedSelectedAccount
        )
        
        saveHubAccountList(updatedAccountList)
    }
    
    private fun saveHubAccountList(accountList: HubAccountList) {
        val accountsJson = gson.toJson(accountList.accounts)
        
        with(sharedPreferences.edit()) {
            putString(KEY_ACCOUNTS, accountsJson)
            putString(KEY_SELECTED_ACCOUNT, accountList.selectedAccount)
            apply()
        }
    }
}