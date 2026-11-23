package io.github.Jackyhu32.wechat.picker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DeliveryPolicyTest {
    @Test
    public void scanCallingActivityReturnsDirectly() {
        assertEquals(
                DeliveryPolicy.Mode.DIRECT_RESULT,
                DeliveryPolicy.decide(
                        true,
                        "com.tencent.mm.plugin.scanner.ui.BaseScanUI",
                        11,
                        false,
                        false,
                        1,
                        false));
    }

    @Test
    public void scanSourceTypeReturnsDirectlyEvenWithoutCallingActivity() {
        assertEquals(
                DeliveryPolicy.Mode.DIRECT_RESULT,
                DeliveryPolicy.decide(false, null, 11, false, false, 1, false));
    }

    @Test
    public void scanExtraReturnsDirectly() {
        assertEquals(
                DeliveryPolicy.Mode.DIRECT_RESULT,
                DeliveryPolicy.decide(true, "com.tencent.mm.ui.LauncherUI", -1, true, false, 1, false));
    }

    @Test
    public void singleSelectNonChatWithCallingActivityReturnsDirectly() {
        assertEquals(
                DeliveryPolicy.Mode.DIRECT_RESULT,
                DeliveryPolicy.decide(true, "com.tencent.mm.plugin.setting.ui.AvatarUI", 0, false, false, 1, false));
    }

    @Test
    public void chatMultiSelectOpensImagePreview() {
        assertEquals(
                DeliveryPolicy.Mode.IMAGE_PREVIEW,
                DeliveryPolicy.decide(false, null, 3, false, true, 9, false));
    }

    @Test
    public void chatWithCallingActivityStillOpensImagePreview() {
        assertEquals(
                DeliveryPolicy.Mode.IMAGE_PREVIEW,
                DeliveryPolicy.decide(true, "com.tencent.mm.ui.LauncherUI", 3, false, true, 9, false));
    }

    @Test
    public void videoSelectionInChatOpensImagePreview() {
        assertEquals(
                DeliveryPolicy.Mode.IMAGE_PREVIEW,
                DeliveryPolicy.decide(false, null, 3, false, true, 9, true));
    }
}
