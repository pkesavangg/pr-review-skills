//
//  SnapshotSkeletonCardView.swift
//  meApp
//
//  Skeleton loading card for the multi-device snapshot overview.
//

import SwiftUI

struct SnapshotSkeletonCardView: View {
    enum Style {
        case weight
        case bloodPressure
        case baby
    }

    @Environment(\.appTheme) private var theme
    let style: Style
    @State private var isAnimating = false

    private var skeletonColor: Color {
        theme.textSubheading.opacity(isAnimating ? 0.28 : 0.16)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            headerSkeleton
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingSM)

            chartSkeleton
                .padding(.horizontal, .spacingXS)
                .padding(.bottom, .spacingSM)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
        .shimmer()
        .onAppear {
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                isAnimating = true
            }
        }
    }

    @ViewBuilder
    private var headerSkeleton: some View {
        switch style {
        case .weight:
            VStack(alignment: .leading, spacing: .spacingXS) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(skeletonColor)
                    .frame(width: 120, height: 12)

                RoundedRectangle(cornerRadius: 6)
                    .fill(skeletonColor)
                    .frame(width: 84, height: 28)
            }
        case .bloodPressure:
            HStack(alignment: .top, spacing: .spacingSM) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(skeletonColor)
                        .frame(width: 92, height: 12)

                    RoundedRectangle(cornerRadius: 6)
                        .fill(skeletonColor)
                        .frame(width: 96, height: 28)
                }

                Spacer()

                VStack(alignment: .leading, spacing: .spacingXS) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(skeletonColor)
                        .frame(width: 48, height: 12)

                    RoundedRectangle(cornerRadius: 6)
                        .fill(skeletonColor)
                        .frame(width: 56, height: 28)
                }
                .frame(width: 72, alignment: .leading)
            }
        case .baby:
            VStack(alignment: .leading, spacing: .spacingXS) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(skeletonColor)
                    .frame(width: 140, height: 12)

                HStack(alignment: .lastTextBaseline, spacing: .spacingSM) {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(skeletonColor)
                        .frame(width: 64, height: 28)

                    RoundedRectangle(cornerRadius: 4)
                        .fill(skeletonColor)
                        .frame(width: 28, height: 12)

                    RoundedRectangle(cornerRadius: 6)
                        .fill(skeletonColor)
                        .frame(width: 56, height: 28)

                    RoundedRectangle(cornerRadius: 4)
                        .fill(skeletonColor)
                        .frame(width: 28, height: 12)
                }
            }
        }
    }

    private var chartSkeleton: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            RoundedRectangle(cornerRadius: 12)
                .fill(skeletonColor.opacity(0.75))
                .frame(height: 160)
                .overlay(
                    VStack(spacing: .spacingSM) {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(theme.backgroundSecondary.opacity(0.5))
                            .frame(height: 2)
                        RoundedRectangle(cornerRadius: 4)
                            .fill(theme.backgroundSecondary.opacity(0.5))
                            .frame(height: 2)
                        RoundedRectangle(cornerRadius: 4)
                            .fill(theme.backgroundSecondary.opacity(0.5))
                            .frame(height: 2)
                    }
                    .padding(.horizontal, .spacingSM)
                )
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        SnapshotSkeletonCardView(style: .weight)
        SnapshotSkeletonCardView(style: .bloodPressure)
        SnapshotSkeletonCardView(style: .baby)
    }
    .padding()
    .background(Color(.systemGroupedBackground))
}
