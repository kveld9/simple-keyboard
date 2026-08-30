package rkr.simplekeyboard.inputmethod.latin.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroupAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class SettingsCardItemDecoration extends RecyclerView.ItemDecoration {
    private final int mCardCornerRadius;
    private final int mCategoryTopMargin;
    private final int mDividerInset;
    private final Paint mCardPaint;
    private final Paint mDividerPaint;

    public SettingsCardItemDecoration(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        mCardCornerRadius = (int) (16 * density);
        mCategoryTopMargin = (int) (12 * density);
        mDividerInset = (int) (16 * density);

        mCardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCardPaint.setStyle(Paint.Style.FILL);

        TypedValue typedValue = new TypedValue();
        int cardColor;
        if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)) {
            cardColor = typedValue.data;
        } else if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)) {
            cardColor = typedValue.data;
        } else {
            cardColor = 0xFF2A2B2E;
        }
        mCardPaint.setColor(cardColor);

        mDividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDividerPaint.setStyle(Paint.Style.STROKE);
        mDividerPaint.setStrokeWidth(1 * density);
        int dividerColor;
        if (context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, typedValue, true)) {
            dividerColor = typedValue.data;
        } else {
            dividerColor = 0x1FFFFFFF;
        }
        mDividerPaint.setColor(dividerColor);
    }

    private boolean isCategory(RecyclerView parent, int position) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter instanceof PreferenceGroupAdapter) {
            PreferenceGroupAdapter prefAdapter = (PreferenceGroupAdapter) adapter;
            if (position >= 0 && position < prefAdapter.getItemCount()) {
                return prefAdapter.getItem(position) instanceof PreferenceCategory;
            }
        }
        return false;
    }

    private boolean isStandalone(RecyclerView parent, int position) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        if (adapter instanceof PreferenceGroupAdapter) {
            PreferenceGroupAdapter prefAdapter = (PreferenceGroupAdapter) adapter;
            if (position >= 0 && position < prefAdapter.getItemCount()) {
                Preference pref = prefAdapter.getItem(position);
                return pref != null && "ime_not_active_banner".equals(pref.getKey());
            }
        }
        return false;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) {
            return;
        }

        if (isCategory(parent, position)) {
            outRect.set(0, position == 0 ? 0 : mCategoryTopMargin, 0, 0);
        } else {
            outRect.set(0, 0, 0, 0);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int childCount = parent.getChildCount();
        if (childCount == 0) return;

        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        int totalItems = adapter != null ? adapter.getItemCount() : 0;

        int groupStart = -1;

        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int adapterPos = parent.getChildAdapterPosition(child);
            if (adapterPos == RecyclerView.NO_POSITION) continue;

            boolean isCat = isCategory(parent, adapterPos);
            boolean isStand = isStandalone(parent, adapterPos);

            if (isCat || isStand) {
                if (groupStart != -1) {
                    drawCardGroup(canvas, parent, groupStart, i - 1);
                    groupStart = -1;
                }
            } else {
                if (groupStart == -1) {
                    groupStart = i;
                }
                boolean isLastInAdapter = (adapterPos == totalItems - 1);
                boolean nextIsExcluded = (!isLastInAdapter && (isCategory(parent, adapterPos + 1) || isStandalone(parent, adapterPos + 1)));
                if (isLastInAdapter || nextIsExcluded || i == childCount - 1) {
                    drawCardGroup(canvas, parent, groupStart, i);
                    groupStart = -1;
                }
            }
        }
    }

    private void drawCardGroup(Canvas canvas, RecyclerView parent, int firstChildIdx, int lastChildIdx) {
        View firstView = parent.getChildAt(firstChildIdx);
        View lastView = parent.getChildAt(lastChildIdx);
        if (firstView == null || lastView == null) return;

        int firstPos = parent.getChildAdapterPosition(firstView);
        int lastPos = parent.getChildAdapterPosition(lastView);
        if (firstPos == RecyclerView.NO_POSITION || lastPos == RecyclerView.NO_POSITION) return;

        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        int totalItems = adapter != null ? adapter.getItemCount() : 0;

        int adapterGroupStart = firstPos;
        while (adapterGroupStart > 0 && !isCategory(parent, adapterGroupStart - 1) && !isStandalone(parent, adapterGroupStart - 1)) {
            adapterGroupStart--;
        }

        int adapterGroupEnd = lastPos;
        while (adapterGroupEnd < totalItems - 1 && !isCategory(parent, adapterGroupEnd + 1) && !isStandalone(parent, adapterGroupEnd + 1)) {
            adapterGroupEnd++;
        }

        boolean isGroupTop = (firstPos == adapterGroupStart);
        boolean isGroupBottom = (lastPos == adapterGroupEnd);

        float left = firstView.getLeft();
        float right = firstView.getRight();
        float top = isGroupTop ? firstView.getTop() : 0;
        float bottom = isGroupBottom ? lastView.getBottom() : parent.getHeight();

        RectF rect = new RectF(left, top, right, bottom);

        float topRadius = isGroupTop ? mCardCornerRadius : 0;
        float bottomRadius = isGroupBottom ? mCardCornerRadius : 0;

        float[] radii = new float[] {
                topRadius, topRadius,
                topRadius, topRadius,
                bottomRadius, bottomRadius,
                bottomRadius, bottomRadius
        };

        Path path = new Path();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        canvas.drawPath(path, mCardPaint);

        int paddingTop = parent.getPaddingTop();
        int paddingBottom = parent.getHeight() - parent.getPaddingBottom();

        for (int i = firstChildIdx; i < lastChildIdx; i++) {
            View currentChild = parent.getChildAt(i);
            if (currentChild != null) {
                float dividerY = currentChild.getBottom();
                if (dividerY >= paddingTop + 1 && dividerY <= paddingBottom - 1) {
                    canvas.drawLine(left + mDividerInset, dividerY, right - mDividerInset, dividerY, mDividerPaint);
                }
            }
        }
    }
}
