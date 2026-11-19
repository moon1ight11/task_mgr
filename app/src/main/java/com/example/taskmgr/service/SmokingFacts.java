package com.example.taskmgr.service;

import java.util.ArrayList;
import java.util.List;

public final class SmokingFacts {

    private SmokingFacts() {
    }

    public static List<Fact> build(SmokingStats s) {
        List<Fact> facts = new ArrayList<>();
        if (s == null) return facts;

        int days = s.daysWithout;

        if (days >= 365) {
            facts.add(new Fact("Год без сигарет",
                "Через год без табака риск ишемической болезни сердца снижается примерно вдвое."));
        }
        if (days >= 90) {
            facts.add(new Fact("3 месяца без сигарет",
                "Через 3 месяца снижается риск инфаркта и инсульта, кровообращение заметно лучше."));
        }
        if (days >= 30) {
            facts.add(new Fact("Месяц без сигарет",
                "Через месяц функция лёгких улучшается, кашель и одышка уменьшаются."));
        }
        if (days >= 14) {
            facts.add(new Fact("14 дней без сигарет",
                "Через 2 недели улучшается кровообращение, физическая нагрузка переносится легче."));
        }
        if (days >= 7) {
            facts.add(new Fact("Неделя без сигарет",
                "Через 7 дней становится легче дышать, вкус и запах уже заметно острее."));
        }
        if (days >= 3) {
            facts.add(new Fact("3 дня без сигарет",
                "Никотин полностью выведен из организма, обостряются вкус и запах."));
        }
        if (days >= 1) {
            facts.add(new Fact("1 день без сигарет",
                "Через 24 часа уровень угарного газа в крови приходит в норму, кислород усваивается лучше."));
        }

        if (s.maxPerDay >= 20) {
            facts.add(new Fact("20+ сигарет в день",
                "В самый активный день вы выкурили " + s.maxPerDay + " сигарет. Это целая пачка — "
                    + "высокая нагрузка на сердце, сосуды и лёгкие."));
        } else if (s.maxPerDay >= 10) {
            facts.add(new Fact("10+ сигарет в день",
                "В самый активный день вы выкурили " + s.maxPerDay + " сигарет. Даже половина пачки "
                    + "заметно повышает риск для сердца и лёгких."));
        }

        if (s.avgPerDay >= 10 && s.maxPerDay < 10) {
            facts.add(new Fact("В среднем 10+ сигарет в день",
                "Ваше среднее — " + s.avgPerDay + " сигарет в день. Это устойчивая ежедневная нагрузка."));
        }

        if (facts.isEmpty() && s.hasData()) {
            facts.add(new Fact("Учёт начат",
                "Данных пока мало. Добавляйте выкуренные сигареты, и здесь появятся факты о прогрессе."));
        }

        return facts;
    }

    public record Fact(String title, String body) {
    }
}
