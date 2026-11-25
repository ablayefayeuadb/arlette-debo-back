package sn.arlettedebo.platform.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.domain.Persistable;

/**
 * Entité à identifiant UUID v7 attribué par l'application (pas d'aller-retour base pour l'id).
 * Implémente {@link Persistable} afin que Spring Data fasse un {@code persist} (et non un {@code merge})
 * sur les nouvelles instances. Égalité fondée sur l'identifiant, compatible avec les proxys Hibernate.
 */
@MappedSuperclass
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = UuidV7.generate();

    @Transient
    private boolean nouveau = true;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nouveau;
    }

    @PostLoad
    @PostPersist
    void marquerPersistee() {
        this.nouveau = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> oClass = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisClass = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : getClass();
        if (thisClass != oClass) {
            return false;
        }
        // getId() et non le champ : sur un proxy Hibernate non initialisé, le champ vaut null
        return getId() != null && Objects.equals(getId(), ((BaseEntity) o).getId());
    }

    /** Identifiant UUID v7 attribué à la construction : le hash reste stable avant et après persistance. */
    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }
}
