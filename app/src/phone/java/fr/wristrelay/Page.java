package fr.wristrelay;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Une page de l'application téléphone : défilement vertical, en-tête, cartes. */
abstract class Page {
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    final PhoneActivity a;
    final ScrollView scroll;
    final LinearLayout col;

    Page(PhoneActivity a) {
        this.a = a;
        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        col = Ui.vbox(a);
        int side = Ui.dp(a, 20);
        col.setPadding(side, Ui.dp(a, 18), side, Ui.dp(a, 28));
        scroll.addView(col, new ScrollView.LayoutParams(MATCH, WRAP));
    }

    View view() {
        return scroll;
    }

    /** Relit l'état (a.snap, a.history) et met l'affichage à jour. */
    abstract void refresh();

    void onShow() { }

    void onHide() { }

    /** Titre de page + sous-titre ; renvoie le sous-titre pour le mettre à jour. */
    TextView header(String title, String subtitle) {
        col.addView(Ui.text(a, title, 30, Ui.TEXT, Ui.MEDIUM), Ui.lp(MATCH, WRAP));
        TextView s = Ui.text(a, subtitle, 14.5f, Ui.TEXT_2);
        col.addView(s, Ui.lpm(a, MATCH, WRAP, 0, 6, 0, 22));
        return s;
    }

    void addCard(View card) {
        col.addView(card, Ui.lpm(a, MATCH, WRAP, 0, 0, 0, 14));
    }

    void addLabel(String text) {
        col.addView(Ui.sectionLabel(a, text), Ui.lpm(a, MATCH, WRAP, 4, 6, 0, 10));
    }
}
