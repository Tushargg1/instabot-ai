import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  Search,
  Sparkles,
  ExternalLink,
  Layers,
  Wrench,
  Briefcase,
  GitBranch,
  GraduationCap,
  Lightbulb,
  User,
  Clock,
  RefreshCw,
  Film,
  Filter,
  Download,
  Copy,
  Check,
  Eye,
  X,
  Boxes,
  LayoutGrid,
} from 'lucide-react'
import { apiClient } from '../api/client'
import { PageResponse } from '../types'

export interface KnowledgeItem {
  id: string
  reelId: string
  title: string
  category: string
  subCategory?: string
  summary?: string
  keyData?: string
  url?: string
  tags?: string[]
  requesterId?: string
  requesterUsername?: string
  requesterName?: string
  requesterAvatarUrl?: string
  sourceReelUrl?: string
  sourceReelTitle?: string
  createdAt: string
}

export interface CategoryCount {
  name: string
  count: number
  subCategories: { name: string; count: number }[]
}

export interface AggregatedEntity {
  title: string
  category: string
  subCategories: string[]
  count: number
  items: KnowledgeItem[]
  keyDataList: string[]
  urls: string[]
  requesters: { username?: string; name?: string; avatar?: string }[]
}

const getCategoryIcon = (category: string) => {
  const cat = category.toLowerCase()
  if (cat.includes('tool') || cat.includes('software')) return Wrench
  if (cat.includes('job') || cat.includes('career') || cat.includes('hiring')) return Briefcase
  if (cat.includes('git') || cat.includes('repo') || cat.includes('code')) return GitBranch
  if (cat.includes('skill') || cat.includes('guide') || cat.includes('tutorial') || cat.includes('learn')) return GraduationCap
  if (cat.includes('project') || cat.includes('idea')) return Lightbulb
  return Layers
}

const getCategoryColor = (category: string) => {
  const cat = category.toLowerCase()
  if (cat.includes('tool')) return 'bg-indigo-50 text-indigo-700 border-indigo-200'
  if (cat.includes('job')) return 'bg-emerald-50 text-emerald-700 border-emerald-200'
  if (cat.includes('git') || cat.includes('code')) return 'bg-blue-50 text-blue-700 border-blue-200'
  if (cat.includes('skill') || cat.includes('guide')) return 'bg-amber-50 text-amber-700 border-amber-200'
  if (cat.includes('project')) return 'bg-purple-50 text-purple-700 border-purple-200'
  return 'bg-gray-100 text-gray-700 border-gray-200'
}

export default function KnowledgeHubPage() {
  const queryClient = useQueryClient()
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL')
  const [selectedSubCategory, setSelectedSubCategory] = useState<string>('ALL')
  const [viewMode, setViewMode] = useState<'cards' | 'aggregated'>('cards')
  const [previewItem, setPreviewItem] = useState<KnowledgeItem | null>(null)
  const [copiedNotion, setCopiedNotion] = useState(false)
  const [page, setPage] = useState(0)

  // Fetch categories with nested subcategories
  const categoriesQuery = useQuery<CategoryCount[]>({
    queryKey: ['knowledge-categories'],
    queryFn: () => apiClient.getKnowledgeCategories(),
    refetchInterval: 10000,
  })
  const categories: CategoryCount[] = categoriesQuery.data || []

  // Fetch items with current filters
  const { data: pageData, isLoading } = useQuery<PageResponse<KnowledgeItem>>({
    queryKey: ['knowledge-items', searchQuery, selectedCategory, selectedSubCategory, page],
    queryFn: () =>
      apiClient.getKnowledgeItems(
        searchQuery || undefined,
        selectedCategory === 'ALL' ? undefined : selectedCategory,
        selectedSubCategory === 'ALL' ? undefined : selectedSubCategory,
        page,
        30
      ),
    placeholderData: (prev) => prev,
  })

  // Mutation to backfill knowledge items from already processed reels
  const backfillMutation = useMutation({
    mutationFn: () => apiClient.backfillKnowledge(),
    onSuccess: (res: any) => {
      queryClient.invalidateQueries({ queryKey: ['knowledge-items'] })
      queryClient.invalidateQueries({ queryKey: ['knowledge-categories'] })
      alert(`Sync Complete! Extracted and categorized ${res.createdCount || 0} items from past reels.`)
    },
  })

  // Compute total items count
  const totalItemsCount = useMemo(() => {
    return categories.reduce((sum, c) => sum + (c.count || 0), 0)
  }, [categories])

  // Get active category's subcategories
  const currentSubCategories = useMemo(() => {
    if (selectedCategory === 'ALL') return []
    const match = categories.find(c => c.name.toLowerCase() === selectedCategory.toLowerCase())
    return match ? match.subCategories : []
  }, [categories, selectedCategory])

  const items: KnowledgeItem[] = pageData?.content || []

  // Entity Aggregation (Deduplication across multiple reels)
  const aggregatedEntities: AggregatedEntity[] = useMemo(() => {
    const map = new Map<string, AggregatedEntity>()

    for (const item of items) {
      const key = item.title.trim().toLowerCase()
      let entity = map.get(key)
      if (!entity) {
        entity = {
          title: item.title,
          category: item.category,
          subCategories: [],
          count: 0,
          items: [],
          keyDataList: [],
          urls: [],
          requesters: [],
        }
        map.set(key, entity)
      }

      entity.count += 1
      entity.items.push(item)
      if (item.subCategory && !entity.subCategories.includes(item.subCategory)) {
        entity.subCategories.push(item.subCategory)
      }
      if (item.keyData && !entity.keyDataList.includes(item.keyData)) {
        entity.keyDataList.push(item.keyData)
      }
      if (item.url && !entity.urls.includes(item.url)) {
        entity.urls.push(item.url)
      }
      if (item.requesterUsername || item.requesterName) {
        const existingReq = entity.requesters.find(r => r.username === item.requesterUsername)
        if (!existingReq) {
          entity.requesters.push({
            username: item.requesterUsername,
            name: item.requesterName,
            avatar: item.requesterAvatarUrl,
          })
        }
      }
    }

    return Array.from(map.values()).sort((a, b) => b.count - a.count)
  }, [items])

  // Export to CSV
  const handleExportCsv = () => {
    if (items.length === 0) return
    const headers = ['Title', 'Category', 'SubCategory', 'Summary', 'Key Data', 'URL', 'Tags', 'Requester', 'Source Reel', 'Created At']
    const rows = items.map(item => [
      `"${(item.title || '').replace(/"/g, '""')}"`,
      `"${(item.category || '').replace(/"/g, '""')}"`,
      `"${(item.subCategory || '').replace(/"/g, '""')}"`,
      `"${(item.summary || '').replace(/"/g, '""')}"`,
      `"${(item.keyData || '').replace(/"/g, '""')}"`,
      `"${(item.url || '').replace(/"/g, '""')}"`,
      `"${(item.tags ? item.tags.join(', ') : '').replace(/"/g, '""')}"`,
      `"${(item.requesterUsername ? '@' + item.requesterUsername : item.requesterName || '').replace(/"/g, '""')}"`,
      `"${(item.sourceReelTitle || '').replace(/"/g, '""')}"`,
      `"${item.createdAt}"`,
    ])

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(e => e.join(','))].join('\n')
    const encodedUri = encodeURI(csvContent)
    const link = document.createElement('a')
    link.setAttribute('href', encodedUri)
    link.setAttribute('download', `reels_knowledge_${new Date().toISOString().slice(0, 10)}.csv`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  // Copy as Notion / Markdown Format
  const handleCopyNotion = () => {
    if (items.length === 0) return
    const md = items.map(item => `### [${item.category}] ${item.title} ${item.subCategory ? `(${item.subCategory})` : ''}
${item.summary ? `> ${item.summary}\n` : ''}
- **⚡ Key Data:** ${item.keyData || 'N/A'}
- **🔗 Link:** ${item.url || 'N/A'}
- **🎬 Source:** [${item.sourceReelTitle || 'Instagram Reel'}](${item.sourceReelUrl || '#'})
- **👤 Asked By:** ${item.requesterUsername ? '@' + item.requesterUsername : 'Follower'}
---`).join('\n\n')

    navigator.clipboard.writeText(md)
    setCopiedNotion(true)
    setTimeout(() => setCopiedNotion(false), 2500)
  }

  return (
    <div className="space-y-6">
      {/* Header & Export Controls */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <Sparkles className="text-indigo-600" size={26} />
            Knowledge Hub & Insights Directory
          </h2>
          <p className="text-sm text-gray-500 mt-1">
            Intelligently classified tools, jobs, repos, skills, and data extracted across all Reels with complete requester provenance.
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={handleExportCsv}
            disabled={items.length === 0}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-white hover:bg-gray-50 text-gray-700 border border-gray-300 rounded-lg text-sm font-medium transition shadow-sm disabled:opacity-50"
            title="Download full dataset as CSV (Google Sheets / Excel compatible)"
          >
            <Download size={15} />
            Export CSV
          </button>

          <button
            onClick={handleCopyNotion}
            disabled={items.length === 0}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-white hover:bg-gray-50 text-gray-700 border border-gray-300 rounded-lg text-sm font-medium transition shadow-sm disabled:opacity-50"
            title="Copy structured Markdown for Notion or Obsidian"
          >
            {copiedNotion ? <Check size={15} className="text-green-600" /> : <Copy size={15} />}
            {copiedNotion ? 'Copied for Notion!' : 'Copy for Notion'}
          </button>

          <button
            onClick={() => backfillMutation.mutate()}
            disabled={backfillMutation.isPending}
            className="inline-flex items-center gap-1.5 px-3 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium transition shadow-sm disabled:opacity-50"
            title="Extract and index items from all existing reels in the database"
          >
            <RefreshCw size={15} className={backfillMutation.isPending ? 'animate-spin' : ''} />
            {backfillMutation.isPending ? 'Extracting...' : 'Sync Past Reels'}
          </button>
        </div>
      </div>

      {/* Global Search Bar */}
      <div className="relative">
        <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-400" size={20} />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => {
            setSearchQuery(e.target.value)
            setPage(0)
          }}
          placeholder="Search across all categories (e.g. 'clipping', 'voice', 'remote', 'elevenlabs', 'salary', or @username)..."
          className="w-full pl-12 pr-4 py-3 bg-white border border-gray-300 rounded-xl text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 shadow-sm transition"
        />
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="absolute right-4 top-1/2 -translate-y-1/2 text-xs text-gray-400 hover:text-gray-600"
          >
            Clear
          </button>
        )}
      </div>

      {/* Top Controls: Category Tabs & View Mode Toggle */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        {/* Top Category Filter Tabs */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-thin">
          <button
            onClick={() => {
              setSelectedCategory('ALL')
              setSelectedSubCategory('ALL')
              setPage(0)
            }}
            className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-sm font-medium transition whitespace-nowrap ${
              selectedCategory === 'ALL'
                ? 'bg-gray-900 text-white shadow-sm'
                : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-200'
            }`}
          >
            <Layers size={15} />
            All Categories
            <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
              selectedCategory === 'ALL' ? 'bg-gray-800 text-gray-200' : 'bg-gray-100 text-gray-600'
            }`}>
              {totalItemsCount}
            </span>
          </button>

          {categories.map((cat) => {
            const Icon = getCategoryIcon(cat.name)
            const isSelected = selectedCategory.toLowerCase() === cat.name.toLowerCase()
            return (
              <button
                key={cat.name}
                onClick={() => {
                  setSelectedCategory(cat.name)
                  setSelectedSubCategory('ALL')
                  setPage(0)
                }}
                className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl text-sm font-medium transition whitespace-nowrap ${
                  isSelected
                    ? 'bg-indigo-600 text-white shadow-sm'
                    : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-200'
                }`}
              >
                <Icon size={15} />
                {cat.name}
                <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                  isSelected ? 'bg-indigo-700 text-white' : 'bg-gray-100 text-gray-600'
                }`}>
                  {cat.count}
                </span>
              </button>
            )
          })}
        </div>

        {/* View Mode Toggle: Cards vs Entity Aggregation */}
        <div className="inline-flex items-center bg-gray-100 p-1 rounded-xl border border-gray-200 shrink-0 self-start sm:self-auto">
          <button
            onClick={() => setViewMode('cards')}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition ${
              viewMode === 'cards'
                ? 'bg-white text-gray-900 shadow-sm font-semibold'
                : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            <LayoutGrid size={14} />
            Cards
          </button>
          <button
            onClick={() => setViewMode('aggregated')}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition ${
              viewMode === 'aggregated'
                ? 'bg-white text-indigo-700 shadow-sm font-semibold'
                : 'text-gray-600 hover:text-gray-900'
            }`}
            title="Group identical tools or jobs together with aggregate mentions"
          >
            <Boxes size={14} />
            Group by Entity ({aggregatedEntities.length})
          </button>
        </div>
      </div>

      {/* Subcategory Filter Pills */}
      {currentSubCategories.length > 0 && (
        <div className="bg-white p-3 rounded-xl border border-gray-200 flex items-center gap-2 flex-wrap">
          <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider flex items-center gap-1 mr-1">
            <Filter size={12} />
            Sub-Parts:
          </span>
          <button
            onClick={() => {
              setSelectedSubCategory('ALL')
              setPage(0)
            }}
            className={`px-3 py-1 rounded-lg text-xs font-medium transition ${
              selectedSubCategory === 'ALL'
                ? 'bg-indigo-100 text-indigo-800 font-semibold'
                : 'text-gray-600 hover:bg-gray-100'
            }`}
          >
            All Subcategories
          </button>
          {currentSubCategories.map((sub) => (
            <button
              key={sub.name}
              onClick={() => {
                setSelectedSubCategory(sub.name)
                setPage(0)
              }}
              className={`px-3 py-1 rounded-lg text-xs font-medium transition flex items-center gap-1.5 ${
                selectedSubCategory.toLowerCase() === sub.name.toLowerCase()
                  ? 'bg-indigo-600 text-white font-semibold shadow-sm'
                  : 'bg-gray-50 text-gray-700 hover:bg-gray-100 border border-gray-200'
              }`}
            >
              {sub.name}
              <span className="opacity-75">({sub.count})</span>
            </button>
          ))}
        </div>
      )}

      {/* Main Content Area */}
      {isLoading ? (
        <div className="text-center py-16 text-gray-500">
          <RefreshCw className="animate-spin inline-block mr-2" size={20} />
          Loading knowledge base...
        </div>
      ) : items.length === 0 ? (
        <div className="bg-white rounded-2xl border border-gray-200 p-12 text-center">
          <div className="w-16 h-16 bg-indigo-50 text-indigo-600 rounded-full flex items-center justify-center mx-auto mb-4">
            <Sparkles size={32} />
          </div>
          <h3 className="text-lg font-bold text-gray-900">No Knowledge Items Found</h3>
          <p className="text-sm text-gray-500 max-w-md mx-auto mt-2">
            {searchQuery
              ? `No items matched your search "${searchQuery}". Try a different keyword.`
              : 'Knowledge items are automatically extracted whenever a Reel is shared to the bot.'}
          </p>
        </div>
      ) : viewMode === 'aggregated' ? (
        /* ── AGGREGATED / DEDUPLICATED ENTITY VIEW ── */
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {aggregatedEntities.map((entity, idx) => {
            const Icon = getCategoryIcon(entity.category)
            const colorClass = getCategoryColor(entity.category)

            return (
              <div
                key={idx}
                className="bg-white rounded-2xl border border-gray-200 hover:border-indigo-400 p-5 shadow-sm hover:shadow-md transition flex flex-col justify-between space-y-4"
              >
                <div className="space-y-3">
                  {/* Category & Mention Count Badge */}
                  <div className="flex items-center justify-between gap-2">
                    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold border ${colorClass}`}>
                      <Icon size={13} />
                      {entity.category}
                    </span>
                    <span className="inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-bold bg-indigo-100 text-indigo-800">
                      <Film size={12} />
                      Mentioned in {entity.count} {entity.count === 1 ? 'Reel' : 'Reels'}
                    </span>
                  </div>

                  {/* Title & External Link */}
                  <div>
                    <h4 className="text-lg font-bold text-gray-900 flex items-center justify-between gap-2">
                      <span>{entity.title}</span>
                      {entity.urls[0] && (
                        <a
                          href={entity.urls[0]}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-gray-400 hover:text-indigo-600 p-1"
                        >
                          <ExternalLink size={16} />
                        </a>
                      )}
                    </h4>
                  </div>

                  {/* Subcategories */}
                  {entity.subCategories.length > 0 && (
                    <div className="flex flex-wrap gap-1">
                      {entity.subCategories.map((sub, i) => (
                        <span key={i} className="text-xs px-2 py-0.5 bg-gray-100 text-gray-600 rounded-md font-medium border border-gray-200">
                          {sub}
                        </span>
                      ))}
                    </div>
                  )}

                  {/* Summary / Description */}
                  {entity.items[0]?.summary && (
                    <p className="text-xs text-gray-600 line-clamp-3 leading-relaxed">
                      {entity.items[0].summary}
                    </p>
                  )}

                  {/* Combined Key Data */}
                  {entity.keyDataList.length > 0 && (
                    <div className="p-3 bg-amber-50/60 border border-amber-200/80 rounded-xl text-xs space-y-1">
                      <span className="font-semibold text-amber-800 flex items-center gap-1">
                        ⚡ Main Points & Key Data:
                      </span>
                      <p className="text-amber-950 font-medium leading-snug">
                        {entity.keyDataList[0]}
                      </p>
                    </div>
                  )}
                </div>

                {/* Aggregated Footer: Requesters & Side-by-Side Preview Trigger */}
                <div className="pt-3 border-t border-gray-100 text-xs flex items-center justify-between gap-2">
                  <div className="flex items-center gap-1.5 text-gray-500">
                    <User size={13} className="text-gray-400" />
                    <span>Requesters:</span>
                    <span className="font-semibold text-gray-800">
                      {entity.requesters.map(r => r.username ? '@' + r.username : r.name).filter(Boolean).slice(0, 2).join(', ') || 'Community'}
                      {entity.requesters.length > 2 ? ` +${entity.requesters.length - 2}` : ''}
                    </span>
                  </div>

                  <button
                    onClick={() => setPreviewItem(entity.items[0])}
                    className="inline-flex items-center gap-1 text-indigo-600 hover:text-indigo-800 font-semibold"
                  >
                    <Eye size={13} />
                    Preview
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      ) : (
        /* ── REGULAR INDIVIDUAL CARDS VIEW ── */
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {items.map((item) => {
            const Icon = getCategoryIcon(item.category)
            const colorClass = getCategoryColor(item.category)

            return (
              <div
                key={item.id}
                className="bg-white rounded-2xl border border-gray-200 hover:border-indigo-300 shadow-sm hover:shadow-md transition flex flex-col justify-between overflow-hidden group"
              >
                <div className="p-5 space-y-3">
                  {/* Category & Subcategory Badge */}
                  <div className="flex items-center justify-between gap-2">
                    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-semibold border ${colorClass}`}>
                      <Icon size={13} />
                      {item.category}
                    </span>
                    {item.subCategory && (
                      <span className="text-xs px-2 py-0.5 bg-gray-100 text-gray-600 rounded-md font-medium border border-gray-200">
                        {item.subCategory}
                      </span>
                    )}
                  </div>

                  {/* Title & Link */}
                  <div>
                    <h4 className="text-base font-bold text-gray-900 group-hover:text-indigo-600 transition flex items-center justify-between gap-2">
                      <span className="truncate">{item.title}</span>
                      <div className="flex items-center gap-1 shrink-0">
                        <button
                          onClick={() => setPreviewItem(item)}
                          className="text-gray-400 hover:text-indigo-600 p-1 rounded"
                          title="Preview Reel & Data"
                        >
                          <Eye size={15} />
                        </button>
                        {item.url && (
                          <a
                            href={item.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-gray-400 hover:text-indigo-600 p-1 rounded"
                            title="Open Resource URL"
                          >
                            <ExternalLink size={15} />
                          </a>
                        )}
                      </div>
                    </h4>
                  </div>

                  {/* Summary */}
                  {item.summary && (
                    <p className="text-xs text-gray-600 line-clamp-3 leading-relaxed">
                      {item.summary}
                    </p>
                  )}

                  {/* Key Data Box */}
                  {item.keyData && (
                    <div className="p-3 bg-amber-50/60 border border-amber-200/80 rounded-xl text-xs space-y-1">
                      <span className="font-semibold text-amber-800 flex items-center gap-1">
                        ⚡ Main Points & Key Data:
                      </span>
                      <p className="text-amber-950 font-medium leading-snug">
                        {item.keyData}
                      </p>
                    </div>
                  )}

                  {/* Tags */}
                  {item.tags && item.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1 pt-1">
                      {item.tags.slice(0, 4).map((t, i) => (
                        <span
                          key={i}
                          className="text-[11px] px-2 py-0.5 bg-gray-50 text-gray-600 rounded-md border border-gray-100"
                        >
                          #{t}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                {/* Provenance Footer: Who Asked, Where From, When */}
                <div className="p-4 bg-gray-50/80 border-t border-gray-100 text-xs text-gray-500 space-y-2">
                  {/* Source Reel */}
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-1.5 truncate">
                      <Film size={13} className="text-gray-400 shrink-0" />
                      <span className="text-gray-400">From:</span>
                      <Link
                        to={`/reels/${item.reelId}`}
                        className="font-medium text-gray-700 hover:text-indigo-600 truncate"
                        title={item.sourceReelTitle || 'View Source Reel'}
                      >
                        {item.sourceReelTitle || 'Source Reel'}
                      </Link>
                    </div>
                    {item.sourceReelUrl && (
                      <a
                        href={item.sourceReelUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-gray-400 hover:text-indigo-600"
                        title="Open on Instagram"
                      >
                        <ExternalLink size={12} />
                      </a>
                    )}
                  </div>

                  {/* Requester & Timestamp */}
                  <div className="flex items-center justify-between gap-2 pt-1 border-t border-gray-200/50">
                    <div className="flex items-center gap-1.5">
                      {item.requesterAvatarUrl ? (
                        <img
                          src={item.requesterAvatarUrl}
                          alt="Avatar"
                          className="w-4 h-4 rounded-full object-cover"
                        />
                      ) : (
                        <User size={13} className="text-gray-400" />
                      )}
                      <span className="text-gray-400">Asked by:</span>
                      <span className="font-semibold text-gray-800">
                        {item.requesterUsername
                          ? `@${item.requesterUsername}`
                          : item.requesterName || (item.requesterId ? `User ${item.requesterId.substring(0, 6)}...` : 'DM Webhook')}
                      </span>
                    </div>

                    <div className="flex items-center gap-1 text-[11px] text-gray-400">
                      <Clock size={11} />
                      <span>{new Date(item.createdAt).toLocaleDateString()}</span>
                    </div>
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      )}

      {/* Pagination */}
      {pageData && pageData.totalPages > 1 && (
        <div className="flex justify-between items-center p-4 bg-white rounded-xl border border-gray-200">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-4 py-2 text-sm border border-gray-300 rounded-lg disabled:opacity-50 hover:bg-gray-50"
          >
            Previous
          </button>
          <span className="text-sm text-gray-500">
            Page {page + 1} of {pageData.totalPages} ({pageData.totalElements} total items)
          </span>
          <button
            onClick={() => setPage((p) => p + 1)}
            disabled={page >= pageData.totalPages - 1}
            className="px-4 py-2 text-sm border border-gray-300 rounded-lg disabled:opacity-50 hover:bg-gray-50"
          >
            Next
          </button>
        </div>
      )}

      {/* ── SIDE-BY-SIDE REEL VIDEO & DATA PREVIEW MODAL ── */}
      {previewItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm">
          <div className="bg-white rounded-2xl shadow-2xl max-w-4xl w-full max-h-[90vh] flex flex-col overflow-hidden animate-in fade-in zoom-in-95 duration-150">
            {/* Modal Header */}
            <div className="p-4 border-b border-gray-200 flex items-center justify-between bg-gray-50">
              <div className="flex items-center gap-2">
                <Film className="text-indigo-600" size={20} />
                <h3 className="text-base font-bold text-gray-900 truncate">
                  Side-by-Side Preview: {previewItem.title}
                </h3>
              </div>
              <button
                onClick={() => setPreviewItem(null)}
                className="p-1.5 text-gray-400 hover:text-gray-700 rounded-lg hover:bg-gray-200 transition"
              >
                <X size={18} />
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-6 overflow-y-auto grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* Left Column: Reel Player / Source Embed */}
              <div className="space-y-4">
                <div className="aspect-[9/16] bg-gray-950 rounded-xl overflow-hidden flex flex-col items-center justify-center text-center p-6 text-white relative shadow-inner">
                  <Film size={48} className="text-indigo-400 mb-3 opacity-80" />
                  <h4 className="font-semibold text-sm max-w-xs mb-2">
                    {previewItem.sourceReelTitle || 'Instagram Reel'}
                  </h4>
                  <p className="text-xs text-gray-400 mb-4 max-w-xs">
                    Watch the original video on Instagram to see the live demonstration
                  </p>
                  {previewItem.sourceReelUrl ? (
                    <a
                      href={previewItem.sourceReelUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-2 px-4 py-2 bg-gradient-to-r from-purple-600 to-pink-600 text-white font-medium text-xs rounded-xl shadow-lg hover:opacity-90 transition"
                    >
                      <ExternalLink size={14} />
                      Open Reel on Instagram
                    </a>
                  ) : (
                    <span className="text-xs text-gray-500">No external link available</span>
                  )}
                </div>

                <div className="p-3 bg-gray-50 border border-gray-200 rounded-xl text-xs space-y-1">
                  <div className="flex items-center justify-between text-gray-500">
                    <span>Source Reel:</span>
                    <Link
                      to={`/reels/${previewItem.reelId}`}
                      className="text-indigo-600 font-semibold hover:underline"
                    >
                      View in Reel Knowledge
                    </Link>
                  </div>
                </div>
              </div>

              {/* Right Column: Extracted Knowledge Breakdown */}
              <div className="space-y-4">
                <div className="space-y-2">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className={`px-2.5 py-1 rounded-md text-xs font-semibold border ${getCategoryColor(previewItem.category)}`}>
                      {previewItem.category}
                    </span>
                    {previewItem.subCategory && (
                      <span className="text-xs px-2 py-0.5 bg-gray-100 text-gray-600 rounded-md font-medium border border-gray-200">
                        {previewItem.subCategory}
                      </span>
                    )}
                  </div>
                  <h2 className="text-2xl font-bold text-gray-900">{previewItem.title}</h2>
                </div>

                {previewItem.summary && (
                  <div className="space-y-1">
                    <h5 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Summary</h5>
                    <p className="text-sm text-gray-700 leading-relaxed bg-gray-50 p-3 rounded-xl border border-gray-100">
                      {previewItem.summary}
                    </p>
                  </div>
                )}

                {previewItem.keyData && (
                  <div className="p-4 bg-amber-50 border border-amber-200 rounded-xl space-y-1.5">
                    <span className="text-xs font-bold text-amber-800 flex items-center gap-1.5">
                      ⚡ Main Points & Key Data
                    </span>
                    <p className="text-sm text-amber-950 font-medium leading-relaxed">
                      {previewItem.keyData}
                    </p>
                  </div>
                )}

                {previewItem.url && (
                  <div className="space-y-1">
                    <h5 className="text-xs font-semibold text-gray-500 uppercase tracking-wider">Direct Resource Link</h5>
                    <a
                      href={previewItem.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-2 text-sm text-indigo-600 hover:text-indigo-800 font-semibold break-all"
                    >
                      <ExternalLink size={14} />
                      {previewItem.url}
                    </a>
                  </div>
                )}

                {/* Provenance Box */}
                <div className="p-4 bg-gray-50 rounded-xl border border-gray-200 space-y-2 text-xs">
                  <h5 className="font-semibold text-gray-700">Origin & Provenance</h5>
                  <div className="flex items-center justify-between text-gray-600">
                    <span className="flex items-center gap-1.5">
                      <User size={13} className="text-gray-400" />
                      Requested By:
                    </span>
                    <span className="font-semibold text-gray-900">
                      {previewItem.requesterUsername
                        ? `@${previewItem.requesterUsername}`
                        : previewItem.requesterName || 'Follower'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-gray-600">
                    <span className="flex items-center gap-1.5">
                      <Clock size={13} className="text-gray-400" />
                      Extracted At:
                    </span>
                    <span>{new Date(previewItem.createdAt).toLocaleString()}</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="p-4 border-t border-gray-200 bg-gray-50 flex justify-end">
              <button
                onClick={() => setPreviewItem(null)}
                className="px-4 py-2 bg-gray-900 text-white rounded-lg text-sm font-medium hover:bg-gray-800 transition"
              >
                Close Preview
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
