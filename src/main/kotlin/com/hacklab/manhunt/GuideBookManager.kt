package com.hacklab.manhunt

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType

/**
 * コマンドガイドブックの生成と管理を行うクラス
 */
class GuideBookManager(private val plugin: Main) {
    
    private val messageManager = plugin.getMessageManager()
    private val guideBookKey = NamespacedKey(plugin, "manhunt_guide")
    
    /**
     * ガイドブックを作成
     */
    fun createGuideBook(player: Player): ItemStack {
        val book = ItemStack(Material.WRITTEN_BOOK)
        val meta = book.itemMeta as BookMeta
        
        // 基本情報設定
        meta.title = messageManager.getMessage(player, "guide-book.title")
        meta.author = messageManager.getMessage(player, "guide-book.author")
        meta.generation = BookMeta.Generation.ORIGINAL
        
        // ページを追加
        val pages = mutableListOf<String>()
        
        // ページ1: 基本コマンド
        pages.add(buildString {
            append(messageManager.getMessage(player, "guide-book.page1.title"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page1.content1"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page1.content2"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page1.content3"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page1.content4"))
        })
        
        // ページ2: チーム連携
        pages.add(buildString {
            append(messageManager.getMessage(player, "guide-book.page2.title"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page2.content1"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page2.content2"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page2.content3"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page2.note"))
        })
        
        // ページ3: バディーシステム
        pages.add(buildString {
            append(messageManager.getMessage(player, "guide-book.page3.title"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page3.content1"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page3.content2"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page3.content3"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page3.content4"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page3.note"))
        })
        
        // ページ4: ショップシステム
        pages.add(buildString {
            append(messageManager.getMessage(player, "guide-book.page4.title"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page4.content1"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page4.content2"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page4.hunter-title"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page4.hunter-earn"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page4.runner-title"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page4.runner-earn"))
        })
        
        // ページ5: 観戦者コマンド
        pages.add(buildString {
            append(messageManager.getMessage(player, "guide-book.page5.title"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page5.content1"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page5.content2"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page5.note"))
        })
        
        // ページ6: ゲームルール
        pages.add(buildString {
            append(messageManager.getMessage(player, "guide-book.page6.title"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page6.hunter-goal"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page6.runner-goal"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page6.time-mode"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page6.endless-mode"))
        })
        
        // ページ7: 仮想コンパス
        pages.add(buildString {
            append(messageManager.getMessage(player, "guide-book.page7.title"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page7.content1"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page7.content2"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page7.content3"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page7.note"))
        })
        
        // ページ8: ヒント
        pages.add(buildString {
            append(messageManager.getMessage(player, "guide-book.page8.title"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page8.tip1"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page8.tip2"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page8.tip3"))
            append("\n")
            append(messageManager.getMessage(player, "guide-book.page8.tip4"))
            append("\n\n")
            append(messageManager.getMessage(player, "guide-book.page8.footer"))
        })
        
        // ページを設定
        meta.pages = pages
        
        // 本を光らせる（特別感を演出）
        meta.addEnchant(Enchantment.UNBREAKING, 1, true)
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        
        // カスタムNBTタグを追加（識別用）
        val container = meta.persistentDataContainer
        container.set(guideBookKey, PersistentDataType.BYTE, 1)
        
        book.itemMeta = meta
        return book
    }
    
    /**
     * アイテムがガイドブックかどうか判定
     */
    fun isGuideBook(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.WRITTEN_BOOK) return false
        val meta = item.itemMeta as? BookMeta ?: return false
        return meta.persistentDataContainer.has(guideBookKey, PersistentDataType.BYTE)
    }
    
    /**
     * プレイヤーがガイドブックを持っているか確認
     */
    fun hasGuideBook(player: Player): Boolean {
        return player.inventory.contents.any { isGuideBook(it) }
    }
    
    /**
     * プレイヤーにガイドブックを付与
     */
    fun giveGuideBook(player: Player): Boolean {
        if (hasGuideBook(player)) {
            player.sendMessage(messageManager.getMessage(player, "guide-book.already-have"))
            return false
        }
        
        val book = createGuideBook(player)
        val leftover = player.inventory.addItem(book)
        
        if (leftover.isEmpty()) {
            player.sendMessage(messageManager.getMessage(player, "guide-book.received"))
            return true
        } else {
            // インベントリがいっぱいの場合は足元にドロップ
            player.world.dropItem(player.location, book)
            player.sendMessage(messageManager.getMessage(player, "guide-book.dropped"))
            return true
        }
    }
}