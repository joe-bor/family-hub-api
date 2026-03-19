package com.familyhub.demo.scheduler;

import com.familyhub.demo.model.FamilyMember;
import com.familyhub.demo.model.GoogleOAuthToken;
import com.familyhub.demo.repository.GoogleOAuthTokenRepository;
import com.familyhub.demo.service.GoogleCalendarSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarSyncSchedulerTest {

    @Mock
    private GoogleOAuthTokenRepository tokenRepository;
    @Mock
    private GoogleCalendarSyncService syncService;

    @InjectMocks
    private GoogleCalendarSyncScheduler scheduler;

    @Test
    void syncAllConnectedMembers_callsSyncMemberForEachToken() {
        UUID memberId1 = UUID.randomUUID();
        UUID memberId2 = UUID.randomUUID();

        GoogleOAuthToken token1 = createTokenWithMember(memberId1);
        GoogleOAuthToken token2 = createTokenWithMember(memberId2);

        when(tokenRepository.findAll()).thenReturn(List.of(token1, token2));

        scheduler.syncAllConnectedMembers();

        verify(syncService).syncMember(memberId1);
        verify(syncService).syncMember(memberId2);
    }

    @Test
    void syncAllConnectedMembers_oneMemberFails_otherStillSynced() {
        UUID memberId1 = UUID.randomUUID();
        UUID memberId2 = UUID.randomUUID();

        GoogleOAuthToken token1 = createTokenWithMember(memberId1);
        GoogleOAuthToken token2 = createTokenWithMember(memberId2);

        when(tokenRepository.findAll()).thenReturn(List.of(token1, token2));
        doThrow(new RuntimeException("Sync failed")).when(syncService).syncMember(memberId1);

        scheduler.syncAllConnectedMembers();

        verify(syncService).syncMember(memberId2);
    }

    private GoogleOAuthToken createTokenWithMember(UUID memberId) {
        FamilyMember member = new FamilyMember();
        member.setId(memberId);
        GoogleOAuthToken token = new GoogleOAuthToken();
        token.setMember(member);
        return token;
    }
}
