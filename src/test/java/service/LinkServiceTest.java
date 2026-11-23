package service;

import model.Config;
import model.Link;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import storage.LinkRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {
    private static final String OWNER = "owner";
    private static final String KEY = "Ab1234";
    private static final String BASE = "https://sho.rt/";
    private static final String ORIGINAL = "https://example.com/path?q=1#fragment";
    private static final String UPDATED = "http://example.com/new";
    @Mock LinkRepository repository;
    private LinkService service;

    @BeforeEach
    void setUp() {
        service = new LinkService(repository, new Config(3, 10, 6, BASE, "redis://localhost:6379"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void createsLinkWithExpectedFields(int requestedMax) {
        when(repository.saveLinkIfAbsent(any())).thenReturn(true);
        String shortUrl = service.createLink(OWNER, ORIGINAL, requestedMax);
        var captor = ArgumentCaptor.forClass(Link.class);
        verify(repository).saveLinkIfAbsent(captor.capture());
        Link saved = captor.getValue();
        assertAll(() -> assertTrue(saved.getShortKey().matches("[A-Za-z0-9]{6}")),
                () -> assertEquals(BASE + saved.getShortKey(), shortUrl),
                () -> assertEquals(OWNER, saved.getOwnerId()),
                () -> assertEquals(ORIGINAL, saved.getOriginalUrl()),
                () -> assertEquals(0, saved.getClickCount()),
                () -> assertEquals(requestedMax == 0 ? 10 : requestedMax, saved.getClickMax()),
                () -> assertEquals(Duration.ofHours(3), Duration.between(saved.getCreatedAt(), saved.getExpiresAt())));
    }

    @Test
    void returnsUrlOfLinkAcceptedAfterCollision() {
        when(repository.saveLinkIfAbsent(any())).thenReturn(false, true);
        String shortUrl = service.createLink(OWNER, UPDATED, 1);
        var captor = ArgumentCaptor.forClass(Link.class);
        verify(repository, times(2)).saveLinkIfAbsent(captor.capture());
        assertEquals(BASE + captor.getAllValues().get(1).getShortKey(), shortUrl);
    }

    @Test
    void failsAfterFiveCollisions() {
        when(repository.saveLinkIfAbsent(any())).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.createLink(OWNER, ORIGINAL, 1));
        verify(repository, times(5)).saveLinkIfAbsent(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "example.com/path", "https:/path", "ftp://example.com", "https://bad host/"})
    void rejectsInvalidOriginalUrlBeforeAccessingRepository(String url) {
        assertThrows(IllegalArgumentException.class, () -> service.createLink(OWNER, url, 1));
        assertThrows(IllegalArgumentException.class, () -> service.updateLink(OWNER, KEY, url));
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNegativeClickLimitBeforeSaving() {
        assertThrows(IllegalArgumentException.class, () -> service.createLink(OWNER, ORIGINAL, -1));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {KEY, "  " + KEY + "  ", BASE + KEY, "sho.rt/" + KEY, "HTTPS://SHO.RT/" + KEY})
    void acceptsSupportedShortUrlForms(String input) {
        Link link = ownedLink(ORIGINAL);
        when(repository.getLink(KEY)).thenReturn(link);
        assertSame(link, service.getLink(OWNER, input));
        verify(repository).getLink(KEY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "bad-key", "https://other.test/Ab1234", "http://sho.rt/Ab1234",
            "https://sho.rt:443/Ab1234", "https://user@sho.rt/Ab1234", "https://sho.rt/Ab1234?q=1",
            "https://sho.rt/Ab1234#fragment", "https://sho.rt/a/Ab1234", "https://sho.rt/",
            "https://sho.rt", "https://sho.rt/bad key"})
    void rejectsMalformedShortUrlBeforeAccessingRepository(String input) {
        assertThrows(IllegalArgumentException.class, () -> service.getLink(OWNER, input));
        verifyNoInteractions(repository);
    }

    @Test
    void refusesToReadOrChangeMissingLink() {
        assertThrows(IllegalArgumentException.class, () -> service.getLink(OWNER, KEY));
        assertThrows(IllegalArgumentException.class, () -> service.updateLink(OWNER, KEY, UPDATED));
        assertThrows(IllegalArgumentException.class, () -> service.deleteLink(OWNER, KEY));
        verify(repository, times(3)).getLink(KEY);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void refusesToReadOrChangeAnotherUsersLink() {
        when(repository.getLink(KEY)).thenReturn(ownedLink(ORIGINAL));
        assertThrows(SecurityException.class, () -> service.getLink("other", KEY));
        assertThrows(SecurityException.class, () -> service.updateLink("other", KEY, UPDATED));
        assertThrows(SecurityException.class, () -> service.deleteLink("other", KEY));
        verify(repository, times(3)).getLink(KEY);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void updatesAndReturnsFreshLink() {
        Link updated = ownedLink(UPDATED);
        when(repository.getLink(KEY)).thenReturn(ownedLink(ORIGINAL), updated);
        when(repository.updateLink(OWNER, KEY, UPDATED)).thenReturn(true);
        assertSame(updated, service.updateLink(OWNER, BASE + KEY, UPDATED));
        var order = inOrder(repository);
        order.verify(repository).getLink(KEY);
        order.verify(repository).updateLink(OWNER, KEY, UPDATED);
        order.verify(repository).getLink(KEY);
    }

    @Test
    void reportsFailedUpdate() {
        when(repository.getLink(KEY)).thenReturn(ownedLink(ORIGINAL));
        assertThrows(IllegalStateException.class, () -> service.updateLink(OWNER, KEY, UPDATED));
        verify(repository).getLink(KEY);
        verify(repository).updateLink(OWNER, KEY, UPDATED);
    }

    @Test
    void deletesLinkAfterCheckingOwnership() {
        when(repository.getLink(KEY)).thenReturn(ownedLink(ORIGINAL));
        when(repository.deleteLink(OWNER, KEY)).thenReturn(true);
        service.deleteLink(OWNER, BASE + KEY);
        var order = inOrder(repository);
        order.verify(repository).getLink(KEY);
        order.verify(repository).deleteLink(OWNER, KEY);
    }

    @Test
    void reportsFailedDelete() {
        when(repository.getLink(KEY)).thenReturn(ownedLink(ORIGINAL));
        assertThrows(IllegalStateException.class, () -> service.deleteLink(OWNER, KEY));
        verify(repository).deleteLink(OWNER, KEY);
    }

    @Test
    void resolvesPublicUrlAndRejectsUnavailableLink() {
        when(repository.resolveLink(KEY)).thenReturn(ORIGINAL, (String) null);
        assertEquals(ORIGINAL, service.resolveLink(BASE + KEY));
        assertThrows(IllegalArgumentException.class, () -> service.resolveLink(KEY));
        verify(repository, times(2)).resolveLink(KEY);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void listsUsersLinksAndExposesConfiguredFormatting() {
        Link link = ownedLink(ORIGINAL);
        when(repository.getUserLinks(OWNER)).thenReturn(List.of(link));
        assertEquals(List.of(link), service.getUserLinks(OWNER));
        assertEquals(BASE + KEY, service.formatShortUrl(link));
        assertEquals(10, service.getDefaultMaxClicks());
    }

    private Link ownedLink(String url) {
        return new Link(OWNER, url, KEY, 0, 10, Instant.EPOCH, null);
    }
}
