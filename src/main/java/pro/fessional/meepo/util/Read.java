package pro.fessional.meepo.util;

import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Auto read `classpath:`, `file:` and URL format.
 * If there is no protocol section, try to read in classpath and file
 *
 * @author trydofor
 * @since 2020-10-31
 */
public class Read {

    private static final String CLAS = "classpath:";
    private static final String FILE = "file:";

    @NotNull
    public static String read(String uri) {
        return read(uri, null, UTF_8);
    }

    @NotNull
    public static String read(@NotNull String uri, String pwd) {
        return read(uri, pwd, UTF_8);
    }

    @NotNull
    public static String read(@NotNull String uri, String pwd, Charset cs) {
        return read(resolve(uri, pwd), cs);
    }

    /**
     * Resolve includes against the containing template, including filesystem paths with spaces.
     */
    @NotNull
    public static String resolve(@NotNull String uri, String pwd) {
        if ((uri.startsWith(".") || uri.startsWith("/"))) {
            URI u = pwd != null && pwd.indexOf(':') >= 0 ? URI.create(pwd)
                : (pwd == null ? Paths.get(".") : Paths.get(pwd)).toAbsolutePath().toUri();
            uri = u.resolve(uri.replace(" ", "%20")).toString();
        }
        return uri;
    }

    /**
     * Normalize include identities without fetching remote resources; resolve existing file aliases.
     */
    public static String identity(String uri) {
        URI resource = URI.create(uri).normalize();
        if ("file".equalsIgnoreCase(resource.getScheme())) {
            Path file = filePath(uri);
            try {
                return file.toRealPath().toUri().toString();
            }
            catch (IOException missing) {
                // A source string can have an origin path that has not been created yet.
                return file.toAbsolutePath().normalize().toUri().toString();
            }
        }
        return resource.toString();
    }

    private static Path filePath(String uri) {
        String path = uri.substring(FILE.length());
        if (path.startsWith("//./") || path.startsWith("//../")) {
            return Paths.get(path.substring(2)); // Preserve the explicit legacy relative-file spelling.
        }
        return Paths.get(URI.create(FILE + path));
    }

    @NotNull
    private static String read(String uri, Charset cs) {
        String str;
        InputStream is = null;
        try {
            if (uri.regionMatches(true, 0, CLAS, 0, CLAS.length())) {
                is = Read.class.getResourceAsStream(uri.substring(CLAS.length()));
            }
            else if (uri.regionMatches(true, 0, FILE, 0, FILE.length())) {
                is = Files.newInputStream(filePath(uri));
            }
            else {
                if (uri.indexOf(':') < 0) {
                    is = Read.class.getResourceAsStream(uri);
                    if (is == null) {
                        try {
                            is = new FileInputStream(uri);
                        }
                        catch (FileNotFoundException e) {
                            // ignore
                        }
                    }
                }

                if (is == null) {
                    URL url = new URI(uri).toURL();
                    URLConnection con = url.openConnection();
                    con.setConnectTimeout(3000);
                    con.setReadTimeout(3000);
                    is = con.getInputStream();
                }
            }
            // auto close Stream
            str = Read.read(is, cs);
        }
        catch (Exception e) {
            throw new IllegalStateException(uri, e);
        }
        return str;
    }

    @NotNull
    public static String read(InputStream is) {
        return read(is, UTF_8);
    }

    /**
     * Read all to string and close InputStream
     */
    @NotNull
    public static String read(InputStream is, Charset cs) {
        return read(new InputStreamReader(is, cs == null ? UTF_8 : cs));
    }

    /**
     * Read all to string and close InputStream
     */
    @NotNull
    public static String read(Reader rd) {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int len;
        try (Reader r0 = rd) {
            while ((len = r0.read(buf)) != -1) {
                sb.append(buf, 0, len);
            }
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return sb.toString();
    }
}
