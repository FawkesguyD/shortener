package storage;

import model.Link;
import java.util.List;

public interface LinkRepository {

    boolean saveLinkIfAbsent(Link link);

    Link getLink(String shortKey);

    String resolveLink(String shortKey);

    List<Link> getUserLinks(String userId);

    boolean updateLink(
            String userId,
            String shortKey,
            String newOriginalUrl
    );

    boolean deleteLink(
            String userId,
            String shortKey
    );
}
