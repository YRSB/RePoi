package com.nobody.repoi.ui.common

import org.jetbrains.compose.resources.DrawableResource
import repoi.shared.generated.resources.Res
import repoi.shared.generated.resources.*

/**
 * 集中管理的 Material Symbols Rounded 图标门面（Issue #189）。
 *
 * 替代原先的图标库依赖，以轻量静态矢量资产访问，
 * 彻底消除全量图标库对编译时长、DEX 方法数以及桌面端包体积的开销。
 */
object PoiIcons {
    // 基础导航与方向
    val ArrowBack: DrawableResource get() = Res.drawable.ic_ms_arrow_back_rounded
    val ArrowForwardIos: DrawableResource get() = Res.drawable.ic_ms_arrow_forward_ios_rounded
    val ArrowDownward: DrawableResource get() = Res.drawable.ic_ms_arrow_downward_rounded
    val ArrowDropDown: DrawableResource get() = Res.drawable.ic_ms_arrow_drop_down_rounded
    val ArrowDropUp: DrawableResource get() = Res.drawable.ic_ms_arrow_drop_up_rounded
    val ArrowUpward: DrawableResource get() = Res.drawable.ic_ms_arrow_upward_rounded
    val ChevronRight: DrawableResource get() = Res.drawable.ic_ms_chevron_right_rounded
    val ExpandLess: DrawableResource get() = Res.drawable.ic_ms_expand_less_rounded
    val ExpandMore: DrawableResource get() = Res.drawable.ic_ms_expand_more_rounded
    val KeyboardArrowDown: DrawableResource get() = Res.drawable.ic_ms_keyboard_arrow_down_rounded
    val KeyboardArrowLeft: DrawableResource get() = Res.drawable.ic_ms_keyboard_arrow_left_rounded
    val KeyboardArrowRight: DrawableResource get() = Res.drawable.ic_ms_keyboard_arrow_right_rounded
    val KeyboardArrowUp: DrawableResource get() = Res.drawable.ic_ms_keyboard_arrow_up_rounded
    val SwapVert: DrawableResource get() = Res.drawable.ic_ms_swap_vert_rounded
    val SyncAlt: DrawableResource get() = Res.drawable.ic_ms_sync_alt_rounded

    // 常用操作与状态
    val Add: DrawableResource get() = Res.drawable.ic_ms_add_rounded
    val AddPhotoAlternate: DrawableResource get() = Res.drawable.ic_ms_add_photo_alternate_rounded
    val Check: DrawableResource get() = Res.drawable.ic_ms_check_rounded
    val Checklist: DrawableResource get() = Res.drawable.ic_ms_checklist_rounded
    val Clear: DrawableResource get() = Res.drawable.ic_ms_close_rounded
    val Close: DrawableResource get() = Res.drawable.ic_ms_close_rounded
    val Done: DrawableResource get() = Res.drawable.ic_ms_done_rounded
    val DoneAll: DrawableResource get() = Res.drawable.ic_ms_done_all_rounded
    val Cancel: DrawableResource get() = Res.drawable.ic_ms_cancel_rounded
    val Block: DrawableResource get() = Res.drawable.ic_ms_block_rounded
    val Refresh: DrawableResource get() = Res.drawable.ic_ms_refresh_rounded
    val Search: DrawableResource get() = Res.drawable.ic_ms_search_rounded
    val Edit: DrawableResource get() = Res.drawable.ic_ms_edit_rounded
    val Delete: DrawableResource get() = Res.drawable.ic_ms_delete_rounded
    val DeleteOutline: DrawableResource get() = Res.drawable.ic_ms_delete_outline_rounded
    val DeleteSweep: DrawableResource get() = Res.drawable.ic_ms_delete_sweep_rounded
    val Download: DrawableResource get() = Res.drawable.ic_ms_download_rounded
    val ContentCopy: DrawableResource get() = Res.drawable.ic_ms_content_copy_rounded
    val ContentPaste: DrawableResource get() = Res.drawable.ic_ms_content_paste_rounded
    val Share: DrawableResource get() = Res.drawable.ic_ms_share_rounded
    val FilterList: DrawableResource get() = Res.drawable.ic_ms_filter_list_rounded
    val GridView: DrawableResource get() = Res.drawable.ic_ms_grid_view_rounded
    val List: DrawableResource get() = Res.drawable.ic_ms_list_rounded
    val Sort: DrawableResource get() = Res.drawable.ic_ms_sort_rounded
    val Tune: DrawableResource get() = Res.drawable.ic_ms_tune_rounded

    // 收藏、喜好与评分
    val Favorite: DrawableResource get() = Res.drawable.ic_ms_favorite_filled_rounded
    val FavoriteBorder: DrawableResource get() = Res.drawable.ic_ms_favorite_rounded
    val Star: DrawableResource get() = Res.drawable.ic_ms_star_filled_rounded
    val StarBorder: DrawableResource get() = Res.drawable.ic_ms_star_rounded
    val ThumbUp: DrawableResource get() = Res.drawable.ic_ms_thumb_up_filled_rounded
    val ThumbUpOutlined: DrawableResource get() = Res.drawable.ic_ms_thumb_up_rounded
    val Bookmarks: DrawableResource get() = Res.drawable.ic_ms_bookmarks_rounded
    val CollectionsBookmark: DrawableResource get() = Res.drawable.ic_ms_collections_bookmark_rounded

    // 用户与认证
    val AccountBox: DrawableResource get() = Res.drawable.ic_ms_account_box_rounded
    val AccountCircle: DrawableResource get() = Res.drawable.ic_ms_account_circle_rounded
    val Person: DrawableResource get() = Res.drawable.ic_ms_person_filled_rounded
    val PersonOutlined: DrawableResource get() = Res.drawable.ic_ms_person_rounded
    val PersonOff: DrawableResource get() = Res.drawable.ic_ms_person_off_rounded
    val People: DrawableResource get() = Res.drawable.ic_ms_people_rounded
    val PeopleOutlined: DrawableResource get() = Res.drawable.ic_ms_people_outline_rounded
    val Groups: DrawableResource get() = Res.drawable.ic_ms_groups_rounded
    val ManageAccounts: DrawableResource get() = Res.drawable.ic_ms_manage_accounts_rounded
    val Fingerprint: DrawableResource get() = Res.drawable.ic_ms_fingerprint_rounded
    val Key: DrawableResource get() = Res.drawable.ic_ms_key_rounded
    val KeyOutlined: DrawableResource get() = Res.drawable.ic_ms_key_outline_rounded
    val Lock: DrawableResource get() = Res.drawable.ic_ms_lock_rounded
    val LockOutlined: DrawableResource get() = Res.drawable.ic_ms_lock_outline_rounded
    val Shield: DrawableResource get() = Res.drawable.ic_ms_shield_rounded
    val Login: DrawableResource get() = Res.drawable.ic_ms_login_rounded
    val Logout: DrawableResource get() = Res.drawable.ic_ms_logout_rounded

    // 社交与消息
    val Comment: DrawableResource get() = Res.drawable.ic_ms_comment_rounded
    val Chat: DrawableResource get() = Res.drawable.ic_ms_chat_rounded
    val ChatBubbleOutline: DrawableResource get() = Res.drawable.ic_ms_chat_bubble_outline_rounded
    val Forum: DrawableResource get() = Res.drawable.ic_ms_forum_rounded
    val ForumOutlined: DrawableResource get() = Res.drawable.ic_ms_forum_outline_rounded
    val Mail: DrawableResource get() = Res.drawable.ic_ms_mail_rounded
    val Email: DrawableResource get() = Res.drawable.ic_ms_email_rounded
    val Inbox: DrawableResource get() = Res.drawable.ic_ms_inbox_rounded
    val Notifications: DrawableResource get() = Res.drawable.ic_ms_notifications_rounded
    val Reply: DrawableResource get() = Res.drawable.ic_ms_reply_rounded
    val Send: DrawableResource get() = Res.drawable.ic_ms_send_rounded
    val Campaign: DrawableResource get() = Res.drawable.ic_ms_campaign_rounded

    // 媒体与相册
    val Image: DrawableResource get() = Res.drawable.ic_ms_image_rounded
    val ImageOutlined: DrawableResource get() = Res.drawable.ic_ms_image_outline_rounded
    val BrokenImage: DrawableResource get() = Res.drawable.ic_ms_broken_image_rounded
    val PhotoAlbum: DrawableResource get() = Res.drawable.ic_ms_photo_album_rounded
    val PhotoLibrary: DrawableResource get() = Res.drawable.ic_ms_photo_library_rounded
    val PhotoLibraryOutlined: DrawableResource get() = Res.drawable.ic_ms_photo_library_outline_rounded
    val Collections: DrawableResource get() = Res.drawable.ic_ms_collections_rounded
    val Movie: DrawableResource get() = Res.drawable.ic_ms_movie_rounded
    val HighQuality: DrawableResource get() = Res.drawable.ic_ms_high_quality_rounded

    // 业务分类与商品
    val Category: DrawableResource get() = Res.drawable.ic_ms_category_rounded
    val CategoryOutlined: DrawableResource get() = Res.drawable.ic_ms_category_outline_rounded
    val Sell: DrawableResource get() = Res.drawable.ic_ms_sell_rounded
    val ShoppingBag: DrawableResource get() = Res.drawable.ic_ms_shopping_bag_rounded
    val CalendarMonth: DrawableResource get() = Res.drawable.ic_ms_calendar_month_rounded
    val CalendarToday: DrawableResource get() = Res.drawable.ic_ms_calendar_today_rounded
    val CalendarTodayOutlined: DrawableResource get() = Res.drawable.ic_ms_calendar_today_outline_rounded
    val Domain: DrawableResource get() = Res.drawable.ic_ms_domain_rounded
    val Description: DrawableResource get() = Res.drawable.ic_ms_description_rounded
    val Article: DrawableResource get() = Res.drawable.ic_ms_article_rounded
    val Style: DrawableResource get() = Res.drawable.ic_ms_style_rounded
    val DynamicFeed: DrawableResource get() = Res.drawable.ic_ms_dynamic_feed_rounded

    // 设备、设置与主题
    val Settings: DrawableResource get() = Res.drawable.ic_ms_settings_rounded
    val Palette: DrawableResource get() = Res.drawable.ic_ms_palette_rounded
    val DarkMode: DrawableResource get() = Res.drawable.ic_ms_dark_mode_rounded
    val LightMode: DrawableResource get() = Res.drawable.ic_ms_light_mode_rounded
    val BrightnessAuto: DrawableResource get() = Res.drawable.ic_ms_brightness_auto_rounded
    val WbSunny: DrawableResource get() = Res.drawable.ic_ms_wb_sunny_rounded
    val NightsStay: DrawableResource get() = Res.drawable.ic_ms_nights_stay_rounded
    val Smartphone: DrawableResource get() = Res.drawable.ic_ms_smartphone_rounded
    val Keyboard: DrawableResource get() = Res.drawable.ic_ms_keyboard_rounded
    val Storage: DrawableResource get() = Res.drawable.ic_ms_storage_rounded
    val Wallpaper: DrawableResource get() = Res.drawable.ic_ms_wallpaper_rounded

    // 其它与标记
    val Home: DrawableResource get() = Res.drawable.ic_ms_home_filled_rounded
    val HomeOutlined: DrawableResource get() = Res.drawable.ic_ms_home_rounded
    val Info: DrawableResource get() = Res.drawable.ic_ms_info_rounded
    val InfoOutlined: DrawableResource get() = Res.drawable.ic_ms_info_outline_rounded
    val Warning: DrawableResource get() = Res.drawable.ic_ms_warning_rounded
    val ErrorOutline: DrawableResource get() = Res.drawable.ic_ms_error_outline_rounded
    val Report: DrawableResource get() = Res.drawable.ic_ms_report_rounded
    val Verified: DrawableResource get() = Res.drawable.ic_ms_verified_rounded
    val Visibility: DrawableResource get() = Res.drawable.ic_ms_visibility_rounded
    val VisibilityOff: DrawableResource get() = Res.drawable.ic_ms_visibility_off_rounded
    val VisibilityOutlined: DrawableResource get() = Res.drawable.ic_ms_visibility_outline_rounded
    val PushPin: DrawableResource get() = Res.drawable.ic_ms_push_pin_rounded
    val QrCode: DrawableResource get() = Res.drawable.ic_ms_qr_code_rounded
    val Link: DrawableResource get() = Res.drawable.ic_ms_link_rounded
    val LocationOn: DrawableResource get() = Res.drawable.ic_ms_location_on_rounded
    val LocationOnOutlined: DrawableResource get() = Res.drawable.ic_ms_location_on_outline_rounded
    val History: DrawableResource get() = Res.drawable.ic_ms_history_rounded
    val Timeline: DrawableResource get() = Res.drawable.ic_ms_timeline_rounded
    val BarChart: DrawableResource get() = Res.drawable.ic_ms_bar_chart_rounded
    val EmojiEmotions: DrawableResource get() = Res.drawable.ic_ms_emoji_emotions_rounded
    val EmojiEvents: DrawableResource get() = Res.drawable.ic_ms_emoji_events_rounded
    val MilitaryTech: DrawableResource get() = Res.drawable.ic_ms_military_tech_rounded
    val NewReleases: DrawableResource get() = Res.drawable.ic_ms_new_releases_rounded
    val Whatshot: DrawableResource get() = Res.drawable.ic_ms_whatshot_rounded
    val Cake: DrawableResource get() = Res.drawable.ic_ms_cake_rounded
    val Wc: DrawableResource get() = Res.drawable.ic_ms_wc_rounded
    val OpenInBrowser: DrawableResource get() = Res.drawable.ic_ms_open_in_browser_rounded
    val MoreHoriz: DrawableResource get() = Res.drawable.ic_ms_more_horiz_rounded
    val MoreVert: DrawableResource get() = Res.drawable.ic_ms_more_vert_rounded
    val SentimentSatisfied: DrawableResource get() = Res.drawable.ic_ms_sentiment_satisfied_rounded

    // 兼容层：对齐旧代码中的 Icons.Filled / Icons.Outlined / Icons.AutoMirrored / Icons.Default
    object Filled {
        val AccountBox: DrawableResource get() = PoiIcons.AccountBox
        val Add: DrawableResource get() = PoiIcons.Add
        val AddPhotoAlternate: DrawableResource get() = PoiIcons.AddPhotoAlternate
        val ArrowDownward: DrawableResource get() = PoiIcons.ArrowDownward
        val ArrowDropDown: DrawableResource get() = PoiIcons.ArrowDropDown
        val ArrowDropUp: DrawableResource get() = PoiIcons.ArrowDropUp
        val ArrowUpward: DrawableResource get() = PoiIcons.ArrowUpward
        val Block: DrawableResource get() = PoiIcons.Block
        val BrokenImage: DrawableResource get() = PoiIcons.BrokenImage
        val Cake: DrawableResource get() = PoiIcons.Cake
        val CalendarMonth: DrawableResource get() = PoiIcons.CalendarMonth
        val CalendarToday: DrawableResource get() = PoiIcons.CalendarToday
        val Campaign: DrawableResource get() = PoiIcons.Campaign
        val Cancel: DrawableResource get() = PoiIcons.Cancel
        val Category: DrawableResource get() = PoiIcons.Category
        val ChatBubbleOutline: DrawableResource get() = PoiIcons.ChatBubbleOutline
        val Check: DrawableResource get() = PoiIcons.Check
        val Checklist: DrawableResource get() = PoiIcons.Checklist
        val ChevronRight: DrawableResource get() = PoiIcons.ChevronRight
        val Clear: DrawableResource get() = PoiIcons.Clear
        val Close: DrawableResource get() = PoiIcons.Close
        val ContentCopy: DrawableResource get() = PoiIcons.ContentCopy
        val ContentPaste: DrawableResource get() = PoiIcons.ContentPaste
        val Delete: DrawableResource get() = PoiIcons.Delete
        val DeleteOutline: DrawableResource get() = PoiIcons.DeleteOutline
        val Description: DrawableResource get() = PoiIcons.Description
        val Done: DrawableResource get() = PoiIcons.Done
        val DoneAll: DrawableResource get() = PoiIcons.DoneAll
        val Download: DrawableResource get() = PoiIcons.Download
        val Edit: DrawableResource get() = PoiIcons.Edit
        val EmojiEmotions: DrawableResource get() = PoiIcons.EmojiEmotions
        val EmojiEvents: DrawableResource get() = PoiIcons.EmojiEvents
        val ExpandLess: DrawableResource get() = PoiIcons.ExpandLess
        val ExpandMore: DrawableResource get() = PoiIcons.ExpandMore
        val Favorite: DrawableResource get() = PoiIcons.Favorite
        val FavoriteBorder: DrawableResource get() = PoiIcons.FavoriteBorder
        val FilterList: DrawableResource get() = PoiIcons.FilterList
        val Fingerprint: DrawableResource get() = PoiIcons.Fingerprint
        val Forum: DrawableResource get() = PoiIcons.Forum
        val GridView: DrawableResource get() = PoiIcons.GridView
        val HighQuality: DrawableResource get() = PoiIcons.HighQuality
        val History: DrawableResource get() = PoiIcons.History
        val Home: DrawableResource get() = PoiIcons.Home
        val Image: DrawableResource get() = PoiIcons.Image
        val Inbox: DrawableResource get() = PoiIcons.Inbox
        val Info: DrawableResource get() = PoiIcons.Info
        val Key: DrawableResource get() = PoiIcons.Key
        val KeyboardArrowDown: DrawableResource get() = PoiIcons.KeyboardArrowDown
        val KeyboardArrowUp: DrawableResource get() = PoiIcons.KeyboardArrowUp
        val Link: DrawableResource get() = PoiIcons.Link
        val LocationOn: DrawableResource get() = PoiIcons.LocationOn
        val Lock: DrawableResource get() = PoiIcons.Lock
        val Mail: DrawableResource get() = PoiIcons.Mail
        val MilitaryTech: DrawableResource get() = PoiIcons.MilitaryTech
        val MoreHoriz: DrawableResource get() = PoiIcons.MoreHoriz
        val MoreVert: DrawableResource get() = PoiIcons.MoreVert
        val Movie: DrawableResource get() = PoiIcons.Movie
        val NewReleases: DrawableResource get() = PoiIcons.NewReleases
        val NightsStay: DrawableResource get() = PoiIcons.NightsStay
        val Notifications: DrawableResource get() = PoiIcons.Notifications
        val OpenInBrowser: DrawableResource get() = PoiIcons.OpenInBrowser
        val People: DrawableResource get() = PoiIcons.People
        val Person: DrawableResource get() = PoiIcons.Person
        val PhotoAlbum: DrawableResource get() = PoiIcons.PhotoAlbum
        val PhotoLibrary: DrawableResource get() = PoiIcons.PhotoLibrary
        val PushPin: DrawableResource get() = PoiIcons.PushPin
        val QrCode: DrawableResource get() = PoiIcons.QrCode
        val Refresh: DrawableResource get() = PoiIcons.Refresh
        val Report: DrawableResource get() = PoiIcons.Report
        val Search: DrawableResource get() = PoiIcons.Search
        val Sell: DrawableResource get() = PoiIcons.Sell
        val Settings: DrawableResource get() = PoiIcons.Settings
        val Share: DrawableResource get() = PoiIcons.Share
        val Shield: DrawableResource get() = PoiIcons.Shield
        val ShoppingBag: DrawableResource get() = PoiIcons.ShoppingBag
        val Star: DrawableResource get() = PoiIcons.Star
        val StarBorder: DrawableResource get() = PoiIcons.StarBorder
        val SyncAlt: DrawableResource get() = PoiIcons.SyncAlt
        val ThumbUp: DrawableResource get() = PoiIcons.ThumbUp
        val Timeline: DrawableResource get() = PoiIcons.Timeline
        val Verified: DrawableResource get() = PoiIcons.Verified
        val Visibility: DrawableResource get() = PoiIcons.Visibility
        val VisibilityOff: DrawableResource get() = PoiIcons.VisibilityOff
        val Warning: DrawableResource get() = PoiIcons.Warning
        val WbSunny: DrawableResource get() = PoiIcons.WbSunny
        val Wc: DrawableResource get() = PoiIcons.Wc
        val Whatshot: DrawableResource get() = PoiIcons.Whatshot
    }

    object Outlined {
        val AccountCircle: DrawableResource get() = PoiIcons.AccountCircle
        val BarChart: DrawableResource get() = PoiIcons.BarChart
        val Block: DrawableResource get() = PoiIcons.Block
        val Bookmarks: DrawableResource get() = PoiIcons.Bookmarks
        val BrightnessAuto: DrawableResource get() = PoiIcons.BrightnessAuto
        val CalendarToday: DrawableResource get() = PoiIcons.CalendarTodayOutlined
        val Category: DrawableResource get() = PoiIcons.CategoryOutlined
        val ChatBubbleOutline: DrawableResource get() = PoiIcons.ChatBubbleOutline
        val Collections: DrawableResource get() = PoiIcons.Collections
        val CollectionsBookmark: DrawableResource get() = PoiIcons.CollectionsBookmark
        val DarkMode: DrawableResource get() = PoiIcons.DarkMode
        val DeleteSweep: DrawableResource get() = PoiIcons.DeleteSweep
        val Domain: DrawableResource get() = PoiIcons.Domain
        val DynamicFeed: DrawableResource get() = PoiIcons.DynamicFeed
        val Email: DrawableResource get() = PoiIcons.Email
        val ErrorOutline: DrawableResource get() = PoiIcons.ErrorOutline
        val FavoriteBorder: DrawableResource get() = PoiIcons.FavoriteBorder
        val Forum: DrawableResource get() = PoiIcons.ForumOutlined
        val Groups: DrawableResource get() = PoiIcons.Groups
        val History: DrawableResource get() = PoiIcons.History
        val Home: DrawableResource get() = PoiIcons.HomeOutlined
        val Image: DrawableResource get() = PoiIcons.ImageOutlined
        val Info: DrawableResource get() = PoiIcons.InfoOutlined
        val Key: DrawableResource get() = PoiIcons.KeyOutlined
        val Keyboard: DrawableResource get() = PoiIcons.Keyboard
        val LightMode: DrawableResource get() = PoiIcons.LightMode
        val Link: DrawableResource get() = PoiIcons.Link
        val LocationOn: DrawableResource get() = PoiIcons.LocationOnOutlined
        val Lock: DrawableResource get() = PoiIcons.LockOutlined
        val ManageAccounts: DrawableResource get() = PoiIcons.ManageAccounts
        val Palette: DrawableResource get() = PoiIcons.Palette
        val People: DrawableResource get() = PoiIcons.PeopleOutlined
        val Person: DrawableResource get() = PoiIcons.PersonOutlined
        val PersonOff: DrawableResource get() = PoiIcons.PersonOff
        val PhotoLibrary: DrawableResource get() = PoiIcons.PhotoLibraryOutlined
        val SentimentSatisfied: DrawableResource get() = PoiIcons.SentimentSatisfied
        val Smartphone: DrawableResource get() = PoiIcons.Smartphone
        val StarBorder: DrawableResource get() = PoiIcons.StarBorder
        val Storage: DrawableResource get() = PoiIcons.Storage
        val Style: DrawableResource get() = PoiIcons.Style
        val SwapVert: DrawableResource get() = PoiIcons.SwapVert
        val ThumbUp: DrawableResource get() = PoiIcons.ThumbUpOutlined
        val Tune: DrawableResource get() = PoiIcons.Tune
        val Visibility: DrawableResource get() = PoiIcons.VisibilityOutlined
        val Wallpaper: DrawableResource get() = PoiIcons.Wallpaper
    }

    object AutoMirrored {
        object Filled {
            val ArrowBack: DrawableResource get() = PoiIcons.ArrowBack
            val ArrowForwardIos: DrawableResource get() = PoiIcons.ArrowForwardIos
            val Comment: DrawableResource get() = PoiIcons.Comment
            val KeyboardArrowLeft: DrawableResource get() = PoiIcons.KeyboardArrowLeft
            val KeyboardArrowRight: DrawableResource get() = PoiIcons.KeyboardArrowRight
            val List: DrawableResource get() = PoiIcons.List
            val Login: DrawableResource get() = PoiIcons.Login
            val Reply: DrawableResource get() = PoiIcons.Reply
            val Send: DrawableResource get() = PoiIcons.Send
            val Sort: DrawableResource get() = PoiIcons.Sort
        }

        object Outlined {
            val Article: DrawableResource get() = PoiIcons.Article
            val Chat: DrawableResource get() = PoiIcons.Chat
            val List: DrawableResource get() = PoiIcons.List
            val Login: DrawableResource get() = PoiIcons.Login
            val Logout: DrawableResource get() = PoiIcons.Logout
        }
    }
}
