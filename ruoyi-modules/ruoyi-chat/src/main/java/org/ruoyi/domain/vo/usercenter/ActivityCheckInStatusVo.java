package org.ruoyi.domain.vo.usercenter;

import lombok.Data;

import java.util.List;

@Data
public class ActivityCheckInStatusVo {

    private boolean checkedToday;

    private int streak;

    private long todayCoins;

    private long dailyReward;

    private long streakBonusHint;

    private int year;

    private int month;

    private List<Integer> checkedDays;
}
