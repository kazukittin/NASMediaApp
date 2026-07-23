package com.ponchi.nasmedia

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import java.io.IOException
import java.util.EnumSet

@UnstableApi
internal class SmbVideoDataSource(
    private val server: String,
    private val username: String,
    private val password: String,
    private val shareName: String,
    private val remotePath: String
) : BaseDataSource(true) {

    private var client: SMBClient? = null
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    private var remoteFile: SmbFile? = null
    private var openedUri: Uri? = null
    private var readPosition = 0L
    private var bytesRemaining = 0L
    private var transferIsOpen = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        try {
            openedUri = dataSpec.uri
            client = SMBClient()
            connection = client!!.connect(server)
            session = connection!!.authenticate(
                AuthenticationContext(username, password.toCharArray(), null)
            )
            share = session!!.connectShare(shareName) as DiskShare
            val fileSize = share!!.getFileInformation(remotePath)
                .standardInformation.endOfFile
            require(dataSpec.position <= fileSize) { "再生位置が動画サイズを超えています" }
            remoteFile = share!!.openFile(
                remotePath,
                EnumSet.of(AccessMask.GENERIC_READ),
                EnumSet.noneOf(FileAttributes::class.java),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            readPosition = dataSpec.position
            bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                fileSize - readPosition
            } else {
                minOf(dataSpec.length, fileSize - readPosition)
            }
            transferIsOpen = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (error: Exception) {
            close()
            throw IOException("SMB動画を開けませんでした", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val bytesToRead = minOf(length.toLong(), bytesRemaining).toInt()
        return try {
            val bytesRead = remoteFile!!.read(buffer, readPosition, offset, bytesToRead)
            if (bytesRead <= 0) {
                C.RESULT_END_OF_INPUT
            } else {
                readPosition += bytesRead
                bytesRemaining -= bytesRead
                bytesTransferred(bytesRead)
                bytesRead
            }
        } catch (error: Exception) {
            throw IOException("SMB動画の読み込みに失敗しました", error)
        }
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        runCatching { remoteFile?.close() }
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { connection?.close() }
        runCatching { client?.close() }
        remoteFile = null
        share = null
        session = null
        connection = null
        client = null
        openedUri = null
        if (transferIsOpen) {
            transferIsOpen = false
            transferEnded()
        }
    }

    class Factory(
        private val server: String,
        private val username: String,
        private val password: String,
        private val shareName: String,
        private val remotePath: String
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbVideoDataSource(
            server,
            username,
            password,
            shareName,
            remotePath
        )
    }
}
