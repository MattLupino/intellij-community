// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.credentialStore.keePass

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.KeePassCredentialStore
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.copy
import com.intellij.util.io.delete
import com.intellij.util.io.move
import com.intellij.util.io.readText
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.nio.file.Path

private val testCredentialAttributes = CredentialAttributes("foo", "u")

internal class KeePassFileManagerTest {
  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  private fun createTestStoreWithCustomMasterKey(baseDirectory: Path = fsRule.fs.getPath("/")): KeePassCredentialStore {
    val store = KeePassCredentialStore(baseDirectory = baseDirectory)
    store.set(testCredentialAttributes, Credentials("u", "p"))
    store.setMasterKey("foo")
    return store
  }

  @Test
  fun clear() {
    val store = createTestStoreWithCustomMasterKey()
    val dbFile = store.dbFile
    TestKeePassFileManager(store).clear()
    assertThat(dbFile).exists()
    assertThat(store.masterKeyFile).exists()
    assertThat(KeePassCredentialStore(store.dbFile, store.masterKeyFile).get(testCredentialAttributes)).isNull()
  }

  @Test
  fun `clear and remove if master password file doesn't exist`() {
    val store = createTestStoreWithCustomMasterKey()
    store.masterKeyFile.delete()
    val dbFile = store.dbFile
    TestKeePassFileManager(store).clear()
    assertThat(dbFile).doesNotExist()
    assertThat(store.masterKeyFile).doesNotExist()
  }

  @Test
  fun `clear and remove if master password file with incorrect master password`() {
    val store = createTestStoreWithCustomMasterKey()

    val oldMasterPasswordFile = store.masterKeyFile.parent.resolve("old.pwd")
    store.masterKeyFile.copy(oldMasterPasswordFile)
    store.setMasterKey("boo")
    oldMasterPasswordFile.copy(store.masterKeyFile)

    val dbFile = store.dbFile
    TestKeePassFileManager(store).clear()
    assertThat(dbFile).doesNotExist()
    assertThat(store.masterKeyFile).exists()
  }

  @Test
  fun `set master password - new database`() {
    val baseDirectory = fsRule.fs.getPath("/")
    TestKeePassFileManager(KeePassCredentialStore(baseDirectory = baseDirectory), masterPasswordRequestAnswer = "boo")
      .askAndSetMasterKey(event = null)

    val store = KeePassCredentialStore(baseDirectory = baseDirectory)
    assertThat(store.dbFile).exists()
    assertThat(store.masterKeyFile.readText()).startsWith("""
      encryption: BUILT_IN
      isAutoGenerated: false
      value: !!binary
    """.trimIndent())
    assertThat(MasterKeyFileStorage(store.masterKeyFile).get()!!.toString(Charsets.UTF_8)).isEqualTo("boo")
  }

  @Test
  fun `set master password - existing database with the same master password but incorrect master key file`() {
    TestKeePassFileManager(createTestStoreWithCustomMasterKey(), masterPasswordRequestAnswer = "foo")
      .askAndSetMasterKey(event = null)

    val store = KeePassCredentialStore(baseDirectory = fsRule.fs.getPath("/"))
    assertThat(store.dbFile).exists()
    assertThat(MasterKeyFileStorage(store.masterKeyFile).get()!!.toString(Charsets.UTF_8)).isEqualTo("foo")
  }

  @Test
  fun `set master password - existing database with the different master password and incorrect master key file`() {
    val existingStore = createTestStoreWithCustomMasterKey()
    fsRule.fs.getPath("/$MASTER_KEY_FILE_NAME").delete()
    TestKeePassFileManager(existingStore, oldMasterPasswordRequestAnswer = "foo", masterPasswordRequestAnswer = "new")
      .askAndSetMasterKey(event = null)

    val store = KeePassCredentialStore(baseDirectory = fsRule.fs.getPath("/"))
    assertThat(store.dbFile).exists()
    assertThat(store.masterKeyFile).exists()
  }

  @Test
  fun `import with custom master key located under imported file dir`() {
    val otherStore = createTestStoreWithCustomMasterKey(fsRule.fs.getPath("/other"))
    otherStore.save()

    val store = KeePassCredentialStore(baseDirectory = fsRule.fs.getPath("/"))
    TestKeePassFileManager(store).import(otherStore.dbFile, event = null)

    checkStoreAfterSuccessfulImport(store)
  }

  private fun checkStoreAfterSuccessfulImport(store: KeePassCredentialStore) {
    store.reload()

    assertThat(store.dbFile).exists()
    assertThat(store.masterKeyFile).exists()

    assertThat(store.get(testCredentialAttributes)!!.getPasswordAsString()).isEqualTo("p")
  }

  @Test
  fun `import with custom master key but key file doesn't exist`() {
    val otherStore = createTestStoreWithCustomMasterKey(fsRule.fs.getPath("/other"))
    otherStore.save()
    fsRule.fs.getPath("/other/${MASTER_KEY_FILE_NAME}").move(fsRule.fs.getPath("/other/otherKey"))

    val store = KeePassCredentialStore(baseDirectory = fsRule.fs.getPath("/"))
    val keePassFileManager = TestKeePassFileManager(store)
    keePassFileManager.import(otherStore.dbFile, event = null)
    assertThat(keePassFileManager.isUnsatisfiedMasterPasswordRequest).isTrue()
    assertThat(store.dbFile).doesNotExist()
    assertThat(store.masterKeyFile).doesNotExist()

    // assert that other store not corrupted
    fsRule.fs.getPath("/other/otherKey").move(fsRule.fs.getPath("/other/${MASTER_KEY_FILE_NAME}"))
    otherStore.reload()
    assertThat(otherStore.get(testCredentialAttributes)!!.getPasswordAsString()).isEqualTo("p")
  }

  @Test
  fun `import with custom master key but key file doesn't exist but user provided new`() {
    val otherStore = createTestStoreWithCustomMasterKey(fsRule.fs.getPath("/other"))
    otherStore.save()
    fsRule.fs.getPath("/other/${MASTER_KEY_FILE_NAME}").delete()

    val store = KeePassCredentialStore(baseDirectory = fsRule.fs.getPath("/"))
    val keePassFileManager = TestKeePassFileManager(store, masterPasswordRequestAnswer = "foo")
    keePassFileManager.import(otherStore.dbFile, event = null)
    assertThat(keePassFileManager.isUnsatisfiedMasterPasswordRequest).isFalse()
    checkStoreAfterSuccessfulImport(store)
  }
}

internal fun KeePassCredentialStore.setMasterKey(value: String) = setMasterPassword(MasterKey(value.toByteArray(), isAutoGenerated = false))

@Suppress("TestFunctionName")
private class TestKeePassFileManager(store: KeePassCredentialStore,
                                     private val masterPasswordRequestAnswer: String? = null,
                                     private val oldMasterPasswordRequestAnswer: String? = null) : KeePassFileManager(store.dbFile, store.masterKeyFile) {
  var isUnsatisfiedMasterPasswordRequest = false

  override fun requestMasterPassword(title: String, contextComponent: Component?, ok: (value: ByteArray) -> String?): Boolean {
    if (masterPasswordRequestAnswer == null) {
      isUnsatisfiedMasterPasswordRequest = true
      return false
    }
    else {
      assertThat(ok(masterPasswordRequestAnswer.toByteArray())).isNull()
      return true
    }
  }

  override fun requestCurrentAndNewKeys(contextComponent: Component?): Boolean {
    doSetNewMasterPassword(oldMasterPasswordRequestAnswer!!.toCharArray(), masterPasswordRequestAnswer!!.toCharArray())
    return true
  }
}