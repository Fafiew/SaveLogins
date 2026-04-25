package com.savelogins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manages storage of encrypted passwords for different servers.
 * Provides secure persistence of server-password mappings.
 */
public class StorageManager {
    private static final String CONFIG_DIR = "savelogins";
    private static final String DATA_FILE = "passwords.json";
    private static final String KEY_FILE = "key.dat";

    private final Path configDirectory;
    private final Path dataFilePath;
    private final Path keyFilePath;
    private final Gson gson;

    private Map<String, String> encryptedPasswords;

    /**
     * Creates a new StorageManager instance.
     */
    public StorageManager() {
        this.configDirectory = getConfigDirectory();
        this.dataFilePath = configDirectory.resolve(DATA_FILE);
        this.keyFilePath = configDirectory.resolve(KEY_FILE);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.encryptedPasswords = new HashMap<>();
    }

    /**
     * Gets the configuration directory path.
     *
     * @return Path to the config directory
     */
    private Path getConfigDirectory() {
        // Use the game directory (current working directory when running in Minecraft)
        String gameDir = System.getProperty("user.dir", ".");
        return Paths.get(gameDir, "config", CONFIG_DIR);
    }

    /**
     * Initializes the storage system.
     * Loads encryption key and password data.
     */
    public void initialize() {
        try {
            Files.createDirectories(configDirectory);
        } catch (IOException e) {
            System.err.println("SaveLogins: Failed to create config directory: " + e.getMessage());
        }

        loadKey();
        loadPasswords();
    }

    /**
     * Loads the encryption key from file, or generates a new one.
     */
    private void loadKey() {
        try {
            if (Files.exists(keyFilePath)) {
                String keyData = Files.readString(keyFilePath).trim();
                EncryptionUtil.initialize(keyData);
            } else {
                EncryptionUtil.initialize(null);
                saveKey();
            }
        } catch (IOException e) {
            EncryptionUtil.initialize(null);
            saveKey();
        }
    }

    /**
     * Saves the encryption key to file.
     */
    private void saveKey() {
        try {
            String keyData = EncryptionUtil.getEncodedKey();
            if (keyData != null) {
                Files.writeString(keyFilePath, keyData);
            }
        } catch (IOException e) {
            System.err.println("SaveLogins: Failed to save encryption key: " + e.getMessage());
        }
    }

    /**
     * Loads passwords from the data file.
     */
    private void loadPasswords() {
        try {
            if (Files.exists(dataFilePath)) {
                try (FileReader reader = new FileReader(dataFilePath.toFile())) {
                    Type mapType = new TypeToken<Map<String, String>>() {}.getType();
                    Map<String, String> loaded = gson.fromJson(reader, mapType);
                    if (loaded != null) {
                        this.encryptedPasswords = loaded;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("SaveLogins: Failed to load passwords: " + e.getMessage());
            this.encryptedPasswords = new HashMap<>();
        }
    }

    /**
     * Saves passwords to the data file.
     */
    private void savePasswords() {
        try (FileWriter writer = new FileWriter(dataFilePath.toFile())) {
            gson.toJson(encryptedPasswords, writer);
        } catch (IOException e) {
            System.err.println("SaveLogins: Failed to save passwords: " + e.getMessage());
        }
    }

    /**
     * Saves an encrypted password for a server.
     *
     * @param serverId The server identifier (IP/domain)
     * @param password The plaintext password
     */
    public void savePassword(String serverId, String password) {
        if (!EncryptionUtil.isInitialized()) {
            System.err.println("SaveLogins: Encryption not initialized");
            return;
        }

        String encrypted = EncryptionUtil.encrypt(password);
        encryptedPasswords.put(serverId, encrypted);
        savePasswords();
    }

    /**
     * Retrieves and decrypts a password for a server.
     *
     * @param serverId The server identifier
     * @return The decrypted password, or empty if not found
     */
    public Optional<String> getPassword(String serverId) {
        String encrypted = encryptedPasswords.get(serverId);
        if (encrypted == null) {
            return Optional.empty();
        }

        try {
            String decrypted = EncryptionUtil.decrypt(encrypted);
            return Optional.of(decrypted);
        } catch (Exception e) {
            System.err.println("SaveLogins: Failed to decrypt password for " + serverId);
            return Optional.empty();
        }
    }

    /**
     * Removes the password for a server.
     *
     * @param serverId The server identifier
     */
    public void removePassword(String serverId) {
        if (encryptedPasswords.remove(serverId) != null) {
            savePasswords();
        }
    }

    /**
     * Checks if a password exists for a server.
     *
     * @param serverId The server identifier
     * @return true if password exists
     */
    public boolean hasPassword(String serverId) {
        return encryptedPasswords.containsKey(serverId);
    }

    /**
     * Gets the list of all stored server IDs.
     *
     * @return Map of server IDs to encrypted passwords
     */
    public Map<String, String> getStoredServers() {
        return new HashMap<>(encryptedPasswords);
    }
}