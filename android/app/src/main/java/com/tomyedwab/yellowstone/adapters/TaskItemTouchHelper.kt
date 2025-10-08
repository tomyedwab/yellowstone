package com.tomyedwab.yellowstone.adapters

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Canvas

class TaskItemTouchHelper(
    private val adapter: TaskAdapter,
    private val onItemMoved: ((Int, Int) -> Unit)? = null
) : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

    private var dragFromPosition: Int = -1
    private var dragToPosition: Int = -1

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition

        // Store the initial drag position
        if (dragFromPosition == -1) {
            dragFromPosition = fromPosition
        }
        dragToPosition = toPosition

        // Only update the visual position, don't call the callback yet
        adapter.moveItem(fromPosition, toPosition)

        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // No swipe functionality needed
    }

    override fun isLongPressDragEnabled(): Boolean {
        return true
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return false
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        
        // Only call the callback if we actually moved the item
        if (dragFromPosition != -1 && dragToPosition != -1 && dragFromPosition != dragToPosition) {
            onItemMoved?.invoke(dragFromPosition, dragToPosition)
        }
        
        // Reset the drag positions
        dragFromPosition = -1
        dragToPosition = -1
        
        // Reset the view alpha and background
        viewHolder.itemView.alpha = 1.0f
        viewHolder.itemView.setBackgroundColor(0) // Reset to transparent
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            // Dim the dragged item to indicate it's being moved
            viewHolder?.itemView?.alpha = 0.7f
        }
    }

    override fun onChildDraw(
        canvas: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        
        // Add visual feedback for the drop target
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
            // Highlight the area where the item will be dropped
            val targetPosition = viewHolder.adapterPosition
            if (targetPosition != RecyclerView.NO_POSITION) {
                val targetView = recyclerView.findViewHolderForAdapterPosition(targetPosition)
                targetView?.itemView?.let { targetItemView ->
                    // Add a subtle background color to indicate drop target
                    targetItemView.setBackgroundColor(0x20CCCCCC) // Light gray with transparency
                }
            }
        }
    }
}