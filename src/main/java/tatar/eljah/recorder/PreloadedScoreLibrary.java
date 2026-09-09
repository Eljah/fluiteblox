package tatar.eljah.recorder;

import java.util.ArrayList;
import java.util.List;

final class PreloadedScoreLibrary {
    private PreloadedScoreLibrary() {
    }

    static List<ScorePiece> buildPieces() {
        List<ScorePiece> pieces = new ArrayList<ScorePiece>();
        pieces.add(piece("preloaded-world-hot-cross-buns", "Hot Cross Buns",
                "B4:q A4:q G4:h B4:q A4:q G4:h G4:q G4:q G4:q G4:q A4:q A4:q A4:q A4:q B4:q A4:q G4:h"));
        pieces.add(piece("preloaded-world-mary-had-a-little-lamb", "Mary Had a Little Lamb",
                "B4:q A4:q G4:q A4:q B4:q B4:q B4:h A4:q A4:q A4:h B4:q D5:q D5:h "
                        + "B4:q A4:q G4:q A4:q B4:q B4:q B4:q B4:q A4:q A4:q B4:q A4:q G4:h"));
        pieces.add(piece("preloaded-world-frere-jacques", "Frere Jacques",
                "C5:q D5:q E5:q C5:q C5:q D5:q E5:q C5:q E5:q F5:q G5:h E5:q F5:q G5:h "
                        + "G5:e A5:e G5:e F5:e E5:q C5:q G5:e A5:e G5:e F5:e E5:q C5:q C5:q G4:q C5:h C5:q G4:q C5:h"));
        pieces.add(piece("preloaded-world-twinkle-twinkle", "Twinkle Twinkle Little Star",
                "C5:q C5:q G5:q G5:q A5:q A5:q G5:h F5:q F5:q E5:q E5:q D5:q D5:q C5:h "
                        + "G5:q G5:q F5:q F5:q E5:q E5:q D5:h G5:q G5:q F5:q F5:q E5:q E5:q D5:h "
                        + "C5:q C5:q G5:q G5:q A5:q A5:q G5:h F5:q F5:q E5:q E5:q D5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-ode-to-joy", "Ode to Joy",
                "E5:q E5:q F5:q G5:q G5:q F5:q E5:q D5:q C5:q C5:q D5:q E5:q E5:q D5:q D5:h "
                        + "E5:q E5:q F5:q G5:q G5:q F5:q E5:q D5:q C5:q C5:q D5:q E5:q D5:q C5:q C5:h"));
        pieces.add(piece("preloaded-world-au-clair-de-la-lune", "Au Clair de la Lune",
                "C5:q C5:q C5:q D5:q E5:h D5:h C5:q E5:q D5:q D5:q C5:h "
                        + "C5:q C5:q C5:q D5:q E5:h D5:h C5:q E5:q D5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-lightly-row", "Lightly Row",
                "G5:q E5:q E5:h F5:q D5:q D5:h C5:q D5:q E5:q F5:q G5:q G5:q G5:h "
                        + "G5:q E5:q E5:h F5:q D5:q D5:h C5:q E5:q G5:q G5:q C5:h"));
        pieces.add(piece("preloaded-world-london-bridge", "London Bridge",
                "G5:q A5:q G5:q F5:q E5:q F5:q G5:h D5:q E5:q F5:h E5:q F5:q G5:h "
                        + "G5:q A5:q G5:q F5:q E5:q F5:q G5:h D5:h G5:q E5:q C5:h"));
        pieces.add(piece("preloaded-world-old-macdonald", "Old MacDonald",
                "G5:q G5:q G5:q D5:q E5:q E5:q D5:h B4:q B4:q A4:q A4:q G4:h "
                        + "D5:q G5:q G5:q G5:q D5:q E5:q E5:q D5:h B4:q B4:q A4:q A4:q G4:h"));
        pieces.add(piece("preloaded-world-jingle-bells", "Jingle Bells",
                "E5:q E5:q E5:h E5:q E5:q E5:h E5:q G5:q C5:q D5:q E5:w "
                        + "F5:q F5:q F5:q F5:q F5:q E5:q E5:q E5:e E5:e E5:q D5:q D5:q E5:q D5:h G5:h"));
        pieces.add(piece("preloaded-world-silent-night", "Silent Night",
                "G4:q A4:q G4:q E4:h G4:q A4:q G4:q E4:h D5:h D5:q B4:h "
                        + "C5:h C5:q G4:h A4:q A4:q C5:q B4:q A4:q G4:q A4:q G4:q E4:h"));
        pieces.add(piece("preloaded-world-amazing-grace", "Amazing Grace",
                "G4:q C5:h E5:q C5:q E5:h D5:q C5:h A4:q G4:h "
                        + "G4:q C5:h E5:q C5:q E5:h D5:q G5:h"));
        pieces.add(piece("preloaded-world-when-the-saints", "When the Saints Go Marching In",
                "C5:q E5:q F5:q G5:h C5:q E5:q F5:q G5:h "
                        + "C5:q E5:q F5:q G5:q E5:q C5:q E5:q D5:h"));
        pieces.add(piece("preloaded-world-aura-lee", "Aura Lee",
                "G4:q C5:q B4:q C5:q D5:q E5:q D5:q C5:q B4:q A4:q G4:h "
                        + "G4:q C5:q B4:q C5:q D5:q E5:q D5:q C5:q B4:q A4:q G4:h"));
        pieces.add(piece("preloaded-world-greensleeves", "Greensleeves",
                "A4:q C5:q D5:q E5:h F5:q E5:q D5:q B4:h G4:q A4:q B4:q C5:h "
                        + "A4:q A4:q G4:q A4:q B4:h G4:q E4:q A4:h"));
        pieces.add(piece("preloaded-world-row-row-row-your-boat", "Row Row Row Your Boat",
                "C5:q C5:q C5:e D5:e E5:q E5:e D5:e E5:e F5:e G5:h "
                        + "C6:e C6:e C6:e G5:e G5:e G5:e E5:e E5:e E5:e C5:e C5:e C5:e "
                        + "G5:q F5:e E5:e D5:e C5:h"));
        pieces.add(piece("preloaded-world-yankee-doodle", "Yankee Doodle",
                "C5:q C5:q D5:q E5:q C5:q E5:q D5:h G4:q C5:q C5:q D5:q E5:q C5:q B4:q C5:h"));
        pieces.add(piece("preloaded-world-skip-to-my-lou", "Skip to My Lou",
                "E5:q C5:q C5:q C5:q D5:q E5:q G5:h E5:q C5:q C5:q C5:q D5:q E5:q C5:h"));
        pieces.add(piece("preloaded-world-camptown-races", "Camptown Races",
                "G5:q E5:q G5:q E5:q G5:q E5:q C5:q D5:q E5:q F5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-oh-susanna", "Oh Susanna",
                "C5:q D5:q E5:q G5:q G5:q A5:q G5:q E5:q C5:q D5:q E5:q E5:q D5:q C5:q D5:h"));
        pieces.add(piece("preloaded-world-minuet-in-g", "Minuet in G",
                "D5:q G4:q A4:q B4:q C5:q D5:q G4:h E5:q C5:q D5:q E5:q F#5:q G5:q G4:h"));
        pieces.add(piece("preloaded-world-eine-kleine-nachtmusik-theme", "Eine kleine Nachtmusik Theme",
                "C5:q G4:q C5:q G4:q C5:q E5:q G5:h F5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-vivaldi-spring-theme", "Spring Theme",
                "E5:e G5:e G5:e G5:e F5:e E5:e D5:e C5:e C5:q D5:q E5:q E5:q D5:h"));
        pieces.add(piece("preloaded-world-pachelbel-canon-theme", "Canon in D Theme",
                "D5:q A4:q B4:q F#4:q G4:q D4:q G4:q A4:q D5:q F#5:q G5:q A5:q B5:q F#5:q G5:q A5:q"));
        pieces.add(piece("preloaded-world-swan-lake-theme", "Swan Lake Theme",
                "A4:q D5:q F5:q A5:q G5:q F5:q E5:q D5:q C#5:q D5:q E5:q F5:q E5:q D5:h"));
        pieces.add(piece("preloaded-world-scarborough-fair", "Scarborough Fair",
                "D5:q D5:q A5:q A5:q E5:q F5:q E5:h D5:q C5:q D5:q E5:q D5:h "
                        + "A4:q C5:q D5:q E5:q F5:q E5:q D5:h"));
        pieces.add(piece("preloaded-world-blue-bells-of-scotland", "The Blue Bells of Scotland",
                "G4:q B4:q D5:q G5:q F5:q E5:q D5:h G4:q B4:q D5:q G5:q A5:q G5:h "
                        + "B5:q A5:q G5:q F5:q E5:q D5:q E5:q F5:q G5:h"));
        pieces.add(piece("preloaded-world-habanera-theme", "Habanera Theme",
                "D5:q C#5:q C5:q B4:q A4:q A4:q G#4:q A4:q B4:q A4:q G#4:q A4:h "
                        + "E5:q D#5:q D5:q C#5:q B4:q B4:q A#4:q B4:q C#5:q B4:q A#4:q B4:h"));
        pieces.add(piece("preloaded-world-largo-new-world-theme", "Largo from New World Symphony",
                "G4:q C5:q E5:q D5:q C5:h E5:q G5:q A5:q G5:q E5:h "
                        + "D5:q E5:q G5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-brahms-lullaby", "Brahms Lullaby",
                "G4:q G4:q Bb4:h G4:q G4:q Bb4:h G4:q Bb4:q E5:q D5:q C5:h C5:q Bb4:q A4:h"));
        pieces.add(piece("preloaded-world-drinken-sailor", "Drunken Sailor",
                "A4:q A4:q A4:q D5:q F5:q F5:q E5:q D5:q E5:q A4:q A4:q A4:q D5:q F5:q E5:h "
                        + "A4:q A4:q A4:q D5:q F5:q F5:q E5:q D5:q E5:q A4:q A4:q G4:q F4:q E4:q D4:h"));
        pieces.add(piece("preloaded-world-house-of-the-rising-sun", "House of the Rising Sun",
                "A4:q C5:q D5:q F5:q A5:q E5:q A5:q E5:q C5:q D5:q F5:q A5:q C6:q D5:q E5:h"));
        pieces.add(piece("preloaded-world-shenandoah", "Shenandoah",
                "G4:q C5:q D5:q E5:q G5:h E5:q D5:q C5:q D5:q E5:h "
                        + "G5:q A5:q G5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-wild-rover", "The Wild Rover",
                "G4:q C5:q C5:q C5:q D5:q E5:q E5:h D5:q C5:q D5:q E5:q G5:h "
                        + "G5:q E5:q D5:q C5:q D5:q E5:q D5:h C5:h"));
        pieces.add(piece("preloaded-world-comin-through-the-rye", "Comin' Through the Rye",
                "G4:q C5:q C5:q D5:q E5:q G5:q E5:h D5:q C5:q D5:q E5:q D5:h "
                        + "G4:q C5:q C5:q D5:q E5:q G5:q A5:q G5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-auld-lang-syne", "Auld Lang Syne",
                "G4:q C5:q C5:q C5:q E5:q D5:q C5:q D5:q E5:q C5:q C5:q E5:q G5:q A5:h "
                        + "A5:q G5:q E5:q E5:q C5:q D5:q C5:q D5:q E5:q C5:q A4:q A4:q G4:h"));
        pieces.add(piece("preloaded-world-danny-boy", "Danny Boy",
                "D5:q E5:q F5:q G5:q A5:h G5:q F5:q E5:q D5:q C5:q D5:h "
                        + "D5:q E5:q F5:q G5:q A5:q C6:q B5:q A5:q G5:h"));
        pieces.add(piece("preloaded-world-home-sweet-home", "Home Sweet Home",
                "C5:q E5:q G5:q E5:q C5:q D5:q E5:h F5:q E5:q D5:q C5:q D5:h "
                        + "C5:q E5:q G5:q E5:q C5:q D5:q E5:q F5:q G5:h"));
        pieces.add(piece("preloaded-world-red-river-valley", "Red River Valley",
                "G4:q C5:q E5:q G5:q A5:q G5:q E5:h D5:q E5:q F5:q E5:q D5:h "
                        + "G4:q C5:q E5:q G5:q A5:q G5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-jolly-good-fellow", "For He's a Jolly Good Fellow",
                "G4:q G4:q A4:q G4:q C5:q B4:h G4:q G4:q A4:q G4:q D5:q C5:h "
                        + "G4:q G4:q G5:q E5:q C5:q B4:q A4:h F5:q F5:q E5:q C5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-good-king-wenceslas", "Good King Wenceslas",
                "G4:q G4:q G4:q A4:q G4:q G4:q D5:h E5:q D5:q C5:q B4:q C5:h "
                        + "G4:q G4:q G4:q A4:q G4:q G4:q D5:h E5:q D5:q C5:q B4:q C5:h"));
        pieces.add(piece("preloaded-world-we-wish-you-merry-christmas", "We Wish You a Merry Christmas",
                "D5:q G5:q G5:e A5:e G5:e F#5:e E5:q E5:q E5:q A5:q A5:e B5:e A5:e G5:e F#5:q D5:q "
                        + "D5:q B5:q B5:e C6:e B5:e A5:e G5:q E5:q D5:e D5:e E5:q A5:q F#5:q G5:h"));
        pieces.add(piece("preloaded-world-god-save-the-king", "God Save the King",
                "G4:q G4:q A4:q F#4:q G4:q A4:q B4:q B4:q C5:q B4:q A4:q G4:h "
                        + "D5:q D5:q E5:q D5:q C5:q B4:q C5:q D5:q E5:q D5:q C5:q B4:h"));
        pieces.add(piece("preloaded-world-la-cucaracha", "La Cucaracha",
                "C5:q C5:q C5:q F5:q A5:h C5:q C5:q C5:q F5:q A5:h "
                        + "F5:q F5:q E5:q E5:q D5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-sakura", "Sakura",
                "A4:q A4:q B4:q A4:q A4:q B4:h A4:q B4:q C5:q B4:q A4:q B4:q A4:h "
                        + "E5:q E5:q C5:q B4:q A4:q B4:h"));
        pieces.add(piece("preloaded-world-fur-elise-theme", "Fur Elise Theme",
                "E5:e D#5:e E5:e D#5:e E5:e B4:e D5:e C5:e A4:q C5:e E5:e A5:q "
                        + "B4:q E5:e G#5:e B5:q C5:q E5:e E5:e D#5:e E5:e D#5:e E5:e B4:e D5:e C5:e A4:h"));
        pieces.add(piece("preloaded-world-turkish-march-theme", "Turkish March Theme",
                "B4:e A4:e G#4:e A4:e C5:q D5:e C5:e B4:e C5:e E5:q F5:e E5:e D#5:e E5:e B5:q "
                        + "A5:e G#5:e A5:e E5:e C5:e A4:e C5:e E5:e A5:h"));
        pieces.add(piece("preloaded-world-blue-danube-theme", "Blue Danube Theme",
                "G5:q G5:q B5:q D6:q D6:h D5:q D5:q G5:q B5:q B5:h "
                        + "F#5:q F#5:q A5:q C6:q C6:h C5:q C5:q F#5:q A5:q A5:h"));
        pieces.add(piece("preloaded-world-can-can-theme", "Can-Can Theme",
                "G5:e G5:e A5:e B5:e C6:e B5:e A5:e G5:e F#5:e F#5:e G5:e A5:e B5:e A5:e G5:e F#5:e "
                        + "E5:e E5:e F#5:e G5:e A5:e G5:e F#5:e E5:e D5:q D5:q G5:h"));
        pieces.add(piece("preloaded-world-william-tell-theme", "William Tell Overture Theme",
                "G4:e G4:e G4:e G4:e C5:q E5:q G5:q E5:q C5:q E5:q G5:h "
                        + "G5:e G5:e G5:e G5:e C6:q B5:q A5:q G5:q E5:q C5:q G4:h"));
        pieces.add(piece("preloaded-world-bridal-chorus", "Bridal Chorus",
                "G4:q C5:q E5:q G5:h E5:q C5:q D5:q E5:q F5:h D5:q B4:q C5:q D5:q E5:h"));
        pieces.add(piece("preloaded-world-wedding-march", "Wedding March",
                "C5:q C5:q C5:q C5:q G5:h E5:q C5:q D5:q E5:q F5:q G5:h "
                        + "A5:q G5:q F5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-world-joy-to-the-world", "Joy to the World",
                "C6:q B5:q A5:q G5:q F5:q E5:q D5:q C5:h G5:q A5:q A5:q B5:q B5:q C6:h "
                        + "C6:q C6:q B5:q A5:q G5:q G5:q F5:q E5:q C6:q C6:q B5:q A5:q G5:h"));
        pieces.add(piece("preloaded-world-deck-the-halls", "Deck the Halls",
                "G5:q F5:e E5:e D5:q C5:q D5:q E5:q C5:q D5:e E5:e F5:e D5:e E5:q D5:q C5:h "
                        + "G5:q F5:e E5:e D5:q C5:q D5:q E5:q C5:q A5:e A5:e A5:e G5:e F5:q E5:q D5:h"));
        pieces.add(piece("preloaded-world-o-christmas-tree", "O Christmas Tree",
                "G4:q C5:q C5:q C5:q D5:e E5:e E5:q E5:q D5:q E5:q F5:q B4:q D5:q C5:h "
                        + "G4:q C5:q C5:q C5:q D5:e E5:e E5:q E5:q D5:q E5:q F5:q B4:q D5:q C5:h"));
        pieces.add(piece("preloaded-tt-tugan-tel-study", "Туган тел (учебная версия)",
                "E5:q E5:q F5:q G5:q A5:h G5:q F5:q E5:q D5:q C5:h "
                        + "E5:q F5:q G5:q A5:q G5:q F5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-tt-apipa-study", "Әпипә (учебная версия)",
                "E5:q E5:q F5:q G5:q A5:q A5:q G5:h F5:q F5:q E5:q D5:q E5:h "
                        + "E5:q G5:q A5:q G5:q F5:q E5:q D5:q C5:q D5:q E5:h"));
        pieces.add(piece("preloaded-tt-kariya-zakariya-study", "Кәрия-Зәкәрия (учебная версия)",
                "G4:q C5:q C5:q D5:q E5:q D5:q C5:h G4:q C5:q C5:q D5:q E5:q F5:q G5:h "
                        + "G5:q F5:q E5:q D5:q C5:q D5:q E5:h C5:h"));
        pieces.add(piece("preloaded-tt-biyu-study", "Бию (учебная версия)",
                "D5:e E5:e F#5:e G5:e A5:q A5:q G5:e F#5:e E5:e D5:e E5:q D5:q "
                        + "D5:e E5:e F#5:e G5:e A5:q B5:q A5:e G5:e F#5:e E5:e D5:h"));
        pieces.add(piece("preloaded-tt-sandugach-kugarchen-study", "Сандугач-күгәрчен (учебная версия)",
                "A4:q C5:q D5:q E5:q F5:h E5:q D5:q C5:q A4:q G4:h "
                        + "A4:q C5:q D5:q E5:q G5:q F5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-tt-almagachlary-study", "Алмагачлары (учебная версия)",
                "G4:q B4:q C5:q D5:q E5:h D5:q C5:q B4:q A4:q G4:h "
                        + "G4:q C5:q D5:q E5:q G5:q E5:q D5:q C5:q B4:q A4:h"));
        pieces.add(piece("preloaded-ru-kak-pod-gorkoy", "Как под горкой",
                "C5:q C5:q D5:q E5:q F5:q E5:q D5:q C5:q G4:q C5:q C5:q D5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-ru-perepelochka", "Перепелочка",
                "E5:q D5:q C5:q D5:q E5:q E5:q E5:h D5:q D5:q D5:h E5:q G5:q G5:h "
                        + "E5:q D5:q C5:q D5:q E5:q E5:q E5:q E5:q D5:q D5:q E5:q D5:q C5:h"));
        pieces.add(piece("preloaded-ru-vo-pole-bereza", "Во поле береза стояла",
                "A4:q A4:q A4:q A4:q G4:q F4:q F4:q E4:q D4:h "
                        + "A4:q A4:q C5:q A4:q G4:q G4:q F4:q F4:q E4:q D4:h "
                        + "E4:q F4:q G4:q F4:q F4:q E4:q D4:h E4:q F4:q G4:q F4:q F4:q E4:q D4:h"));
        pieces.add(piece("preloaded-ru-akh-vy-seni", "Ах вы, сени",
                "C5:q C5:q F5:q F5:q F5:q F5:q E5:q C5:q C5:q C5:q F5:q F5:q F5:q F5:q E5:h "
                        + "G5:q E5:q D5:q D5:q D5:q D5:q E5:q C5:q G5:q E5:q D5:q D5:q D5:q D5:q C5:h"));
        pieces.add(piece("preloaded-ru-zhili-u-babusi", "Жили у бабуси",
                "F5:q E5:q D5:q C5:q G5:h F5:q E5:q D5:q C5:q G5:h "
                        + "F5:q A5:q A5:q F5:q E5:q G5:q G5:q E5:q D5:q E5:q F5:q D5:q C5:h"));
        return pieces;
    }

    private static ScorePiece piece(String id, String title, String spec) {
        ScorePiece piece = new ScorePiece();
        piece.id = id;
        piece.title = title;
        piece.createdAt = 2L;
        String[] tokens = spec.split(" ");
        int measure = 1;
        int units = 0;
        for (String token : tokens) {
            if (token.length() == 0) continue;
            String[] parts = token.split(":");
            String pitch = parts[0];
            String duration = parts.length > 1 ? duration(parts[1]) : "quarter";
            int durationUnits = durationUnits(duration);
            if (units + durationUnits > 64) {
                measure++;
                units = 0;
            }
            piece.notes.add(new NoteEvent(pitch.substring(0, pitch.length() - 1),
                    Integer.parseInt(pitch.substring(pitch.length() - 1)), duration, measure));
            units += durationUnits;
        }
        return piece;
    }

    private static String duration(String value) {
        if ("w".equals(value)) return "whole";
        if ("h".equals(value)) return "half";
        if ("e".equals(value)) return "eighth";
        if ("s".equals(value)) return "16th";
        return "quarter";
    }

    private static int durationUnits(String duration) {
        if ("whole".equals(duration)) return 64;
        if ("half".equals(duration)) return 32;
        if ("eighth".equals(duration)) return 8;
        if ("16th".equals(duration)) return 4;
        return 16;
    }
}
