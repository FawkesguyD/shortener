package controller;

import model.Link;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import service.LinkService;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ControllerTest {
    private static final String USER = "owner";
    private static final String KEY = "Ab1234";
    private static final String ORIGINAL = "https://example.com/";
    private static final String SHORT = "https://sho.rt/" + KEY;
    @Mock LinkService service;

    @Test
    void delegatesLinkManagementUsingCurrentUser() {
        var controller = new Controller(service, USER);
        Link link = new Link(USER, ORIGINAL, KEY, 0, 3, Instant.EPOCH, null);
        when(service.createLink(USER, ORIGINAL, 3)).thenReturn(SHORT);
        when(service.getLink(USER, KEY)).thenReturn(link);
        when(service.updateLink(USER, KEY, ORIGINAL)).thenReturn(link);
        when(service.getUserLinks(USER)).thenReturn(List.of(link));
        when(service.formatShortUrl(link)).thenReturn(SHORT);
        when(service.getDefaultMaxClicks()).thenReturn(10);

        assertEquals(USER, controller.getUserId());
        assertEquals(SHORT, controller.createLink(ORIGINAL, 3));
        assertSame(link, controller.getLink(KEY));
        assertSame(link, controller.updateLink(KEY, ORIGINAL));
        assertEquals(List.of(link), controller.getUserLinks());
        assertEquals(SHORT, controller.formatShortUrl(link));
        assertEquals(10, controller.getDefaultMaxClicks());
        controller.deleteLink(KEY);
        verify(service).deleteLink(USER, KEY);
    }

    @Test
    void doesNotResolveWhenDesktopOrBrowseIsUnsupported() {
        var controller = new Controller(service, USER);
        try (var desktop = mockStatic(Desktop.class)) {
            assertThrows(IllegalStateException.class, () -> controller.openLink(KEY));
            desktop.when(Desktop::isDesktopSupported).thenReturn(true);
            desktop.when(Desktop::getDesktop).thenReturn(mock(Desktop.class));
            assertThrows(IllegalStateException.class, () -> controller.openLink(KEY));
        }
        verifyNoInteractions(service);
    }

    @Test
    void opensResolvedUriAndWrapsBrowseFailures() throws IOException {
        var controller = new Controller(service, USER);
        Desktop browser = mock(Desktop.class);
        when(browser.isSupported(Desktop.Action.BROWSE)).thenReturn(true);
        URI uri = URI.create(ORIGINAL);
        IOException io = new IOException("browse failed");
        SecurityException denied = new SecurityException("browse denied");
        doNothing().doThrow(io, denied).when(browser).browse(uri);
        when(service.resolveLink(KEY)).thenReturn(ORIGINAL, ORIGINAL, ORIGINAL, "https://bad host/");

        try (var desktop = mockStatic(Desktop.class)) {
            desktop.when(Desktop::isDesktopSupported).thenReturn(true);
            desktop.when(Desktop::getDesktop).thenReturn(browser);
            controller.openLink(KEY);
            assertSame(io, assertThrows(IllegalStateException.class, () -> controller.openLink(KEY)).getCause());
            assertSame(denied, assertThrows(IllegalStateException.class, () -> controller.openLink(KEY)).getCause());
            assertInstanceOf(URISyntaxException.class,
                    assertThrows(IllegalStateException.class, () -> controller.openLink(KEY)).getCause());
        }
        verify(browser, times(3)).browse(uri);
    }
}
