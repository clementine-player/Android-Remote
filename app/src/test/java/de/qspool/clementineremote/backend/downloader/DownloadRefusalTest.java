package de.qspool.clementineremote.backend.downloader;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import de.qspool.clementineremote.backend.elements.DownloaderResult;
import de.qspool.clementineremote.backend.elements.DownloaderResult.DownloadResult;
import de.qspool.clementineremote.backend.pb.ClementineMessage;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.MsgType;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ReasonDisconnect;
import de.qspool.clementineremote.backend.pb.ClementineRemoteProtocolBuffer.ResponseDisconnect;

/** Why Clementine closed a download. */
public class DownloadRefusalTest {

    private static ClementineMessage disconnect(ReasonDisconnect reason) {
        return new ClementineMessage(ClementineMessage.getMessageBuilder(MsgType.DISCONNECT)
                .setResponseDisconnect(ResponseDisconnect.newBuilder().setReasonDisconnect(reason)));
    }

    @Test
    public void notOnTheLocalNetwork() {
        assertEquals(DownloadResult.NOT_LOCAL_NETWORK,
                DownloaderResult.refusal(disconnect(ReasonDisconnect.Not_Local_Network)));
    }

    @Test
    public void downloadsNotAllowed() {
        assertEquals(DownloadResult.FOBIDDEN,
                DownloaderResult.refusal(disconnect(ReasonDisconnect.Download_Forbidden)));
    }
}
