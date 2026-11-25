package sn.arlettedebo.platform.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Égalité JPA : même id ⇒ égales ; classes distinctes ⇒ non. */
@DisplayName("BaseEntity : égalité et hashCode")
class BaseEntityTests {

    private static final class EntiteA extends BaseEntity {
    }

    private static final class EntiteB extends BaseEntity {
    }

    @Test
    @DisplayName("Identité, null, type différent, id distincts / identiques")
    void egalite() {
        EntiteA a = new EntiteA();
        EntiteA memeRef = a;
        assertThat(memeRef).isSameAs(a).isNotNull();

        EntiteA autre = new EntiteA();
        Object autreClasse = new EntiteB();
        assertThat(a).isNotEqualTo(autre).isNotEqualTo(autreClasse);

        EntiteA meme = new EntiteA();
        setId(meme, a.getId());
        assertThat(a).isEqualTo(meme).hasSameHashCodeAs(meme);
        assertThat(a.isNew()).isTrue();
        a.marquerPersistee();
        assertThat(a.isNew()).isFalse();
    }

    @Test
    @DisplayName("hashCode stable après marquage persisté")
    void hashStable() {
        EntiteA a = new EntiteA();
        int h = a.hashCode();
        a.marquerPersistee();
        assertThat(a.hashCode()).isEqualTo(h);
        assertThat(a.getId()).isNotNull();
    }

    private static void setId(BaseEntity e, UUID id) {
        try {
            var f = BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
