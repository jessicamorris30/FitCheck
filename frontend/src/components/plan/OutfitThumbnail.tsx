import type { ClothingItem } from '../../types'

/**
 * A small square preview of an outfit: its first four items in a 2×2 collage.
 * Fewer items just leave the remaining squares empty.
 */
export default function OutfitThumbnail({ items }: { items: ClothingItem[] }) {
  const firstFour = items.slice(0, 4)
  return (
    <div className="grid aspect-square grid-cols-2 grid-rows-2 gap-0.5 overflow-hidden rounded-lg bg-gray-200">
      {Array.from({ length: 4 }, (_, i) => {
        const item = firstFour[i]
        return item ? (
          <img key={item.id} src={item.imageUrl} alt="" loading="lazy" className="h-full w-full object-cover" />
        ) : (
          <div key={`empty-${i}`} className="bg-gray-100" />
        )
      })}
    </div>
  )
}
