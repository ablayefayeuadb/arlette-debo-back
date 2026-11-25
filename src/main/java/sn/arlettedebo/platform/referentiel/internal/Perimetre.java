package sn.arlettedebo.platform.referentiel.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import sn.arlettedebo.platform.shared.error.DomainException;

/** Salon : unité de cloisonnement des données (comptes, caisse, clientes, stocks, coin boutique…). */
@Getter
@Entity
@Table(name = "tp_perimetre")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Perimetre extends Referentiel {

    @Column(name = "type", nullable = false, updatable = false)
    private String type;

    @Column(name = "ordre", nullable = false)
    private short ordre;

    @Column(name = "actif", nullable = false)
    private boolean actif;

    /** Places proposées par créneau horaire (grille type / nouveaux créneaux). */
    @Column(name = "capacite_creneau", nullable = false)
    private short capaciteCreneau = 1;

    /** Début de la grille type (heure entière, inclusive), ex. 9 → 09:00. */
    @Column(name = "heure_ouverture", nullable = false)
    private short heureOuverture = 9;

    /** Fin de la grille type (heure entière, exclusive), ex. 18 → dernier créneau 17:00–18:00. */
    @Column(name = "heure_fermeture", nullable = false)
    private short heureFermeture = 18;

    Perimetre(String code, String libelle, String type, short ordre) {
        super(code, libelle);
        this.type = type;
        this.ordre = ordre;
        this.actif = true;
        this.capaciteCreneau = 1;
        this.heureOuverture = 9;
        this.heureFermeture = 18;
    }

    void modifier(String libelle, boolean actif) {
        renommer(libelle);
        this.actif = actif;
    }

    void definirParametresCreneaux(int capacite, int ouverture, int fermeture) {
        if (capacite < 1 || capacite > 20) {
            throw DomainException.invalide("Capacité entre 1 et 20 places par créneau.");
        }
        if (ouverture < 0 || ouverture > 23 || fermeture < 1 || fermeture > 24 || fermeture <= ouverture) {
            throw DomainException.invalide("Plage horaire invalide (ex. ouverture 9, fermeture 18).");
        }
        this.capaciteCreneau = (short) capacite;
        this.heureOuverture = (short) ouverture;
        this.heureFermeture = (short) fermeture;
    }
}
