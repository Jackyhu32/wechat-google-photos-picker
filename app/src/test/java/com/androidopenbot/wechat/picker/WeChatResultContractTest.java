package com.androidopenbot.wechat.picker;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeChatResultContractTest {
    @Test
    public void imagesOnlyUseCropListAndNotVideoKeys() {
        Map<String, Object> extras = WeChatResultContract.build(Arrays.asList(
                image("/a.jpg"),
                image("/a.jpg"),
                image("/b.jpg")
        ), false, Collections.singletonMap("GalleryUI_ToUser", "wxid_friend"));

        assertEquals(Arrays.asList("/a.jpg", "/b.jpg"), extras.get(WeChatResultContract.CROP_LIST));
        assertEquals("/a.jpg", extras.get(WeChatResultContract.CROP_SINGLE));
        assertEquals(Boolean.TRUE, extras.get(WeChatResultContract.COMPRESS));
        assertEquals(Boolean.FALSE, extras.get(WeChatResultContract.SEND_RAW));
        assertEquals(Boolean.FALSE, extras.get(WeChatResultContract.IS_TAKE_PHOTO));
        assertEquals("wxid_friend", extras.get("GalleryUI_ToUser"));
        assertFalse(extras.containsKey(WeChatResultContract.VIDEO_LIST));
        assertFalse(extras.containsKey(WeChatResultContract.SEG_VIDEO));
        assertFalse(extras.containsKey(WeChatResultContract.MIX_LIST));
    }

    @Test
    public void singleVideoUsesSightKeysAndSkipsTheImageList() {
        Map<String, Object> extras = WeChatResultContract.build(Collections.singletonList(
                new WeChatResultContract.Item(true, "/v.mp4", "/v.jpg", "abc")
        ), true, Collections.emptyMap());

        assertFalse(extras.containsKey(WeChatResultContract.CROP_LIST));
        assertEquals(Collections.singletonList("/v.mp4"), extras.get(WeChatResultContract.VIDEO_LIST));
        assertEquals("/v.mp4", extras.get(WeChatResultContract.SEG_VIDEO));
        assertEquals("/v.mp4", extras.get(WeChatResultContract.SIGHT_PATH));
        assertEquals("/v.jpg", extras.get(WeChatResultContract.SIGHT_THUMB));
        assertEquals("abc", extras.get(WeChatResultContract.SIGHT_MD5));
        assertEquals(Boolean.FALSE, extras.get(WeChatResultContract.COMPRESS));
        assertEquals(Boolean.TRUE, extras.get(WeChatResultContract.SEND_RAW));
        assertFalse(extras.containsKey(WeChatResultContract.MIX_LIST));
    }

    @Test
    public void mixedSelectionFillsBothListsWithoutASingleVideoContract() {
        Map<String, Object> extras = WeChatResultContract.build(Arrays.asList(
                image("/a.jpg"),
                new WeChatResultContract.Item(true, "/v.mp4", null, null)
        ), false, Collections.emptyMap());

        assertEquals(Collections.singletonList("/a.jpg"), extras.get(WeChatResultContract.CROP_LIST));
        assertEquals(Collections.singletonList("/v.mp4"), extras.get(WeChatResultContract.VIDEO_LIST));
        assertEquals(Arrays.asList("/a.jpg", "/v.mp4"), extras.get(WeChatResultContract.MIX_LIST));
        assertFalse(extras.containsKey(WeChatResultContract.SEG_VIDEO));
    }

    @Test
    public void emptySelectionIsNull() {
        assertNull(WeChatResultContract.build(Collections.emptyList(), false, Collections.emptyMap()));
        assertNull(WeChatResultContract.build(
                Collections.singletonList(new WeChatResultContract.Item(false, "", null, null)),
                false,
                Collections.emptyMap()));
    }

    @Test
    public void previewListIsASeparateCopy() {
        Map<String, Object> extras = WeChatResultContract.build(
                Collections.singletonList(image("/a.jpg")), false, Collections.emptyMap());
        @SuppressWarnings("unchecked")
        List<String> crop = (ArrayList<String>) extras.get(WeChatResultContract.CROP_LIST);
        @SuppressWarnings("unchecked")
        List<String> preview = (ArrayList<String>) extras.get(WeChatResultContract.PREVIEW_LIST);
        crop.add("/mutated.jpg");
        assertEquals(Collections.singletonList("/a.jpg"), preview);
    }

    private static WeChatResultContract.Item image(String path) {
        return new WeChatResultContract.Item(false, path, null, null);
    }
}
