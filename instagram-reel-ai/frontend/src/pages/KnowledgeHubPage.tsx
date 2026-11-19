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
        24
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

  return (
    <div className="space-y-6">
      {/* Header */}
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

        <button
          onClick={() => backfillMutation.mutate()}
          disabled={backfillMutation.isPending}
          className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium transition disabled:opacity-50 shadow-sm"
          title="Extract and index items from all existing reels in the database"
        >
          <RefreshCw size={16} className={backfillMutation.isPending ? 'animate-spin' : ''} />
          {backfillMutation.isPending ? 'Extracting...' : 'Sync Past Reels'}
        </button>
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

      {/* Top Category Filter Tabs */}
      <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-thin">
        <button
          onClick={() => {
            setSelectedCategory('ALL')
            setSelectedSubCategory('ALL')
            setPage(0)
          }}
          className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition whitespace-nowrap ${
            selectedCategory === 'ALL'
              ? 'bg-gray-900 text-white shadow-sm'
              : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-200'
          }`}
        >
          <Layers size={16} />
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
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium transition whitespace-nowrap ${
                isSelected
                  ? 'bg-indigo-600 text-white shadow-sm'
                  : 'bg-white text-gray-700 hover:bg-gray-100 border border-gray-200'
              }`}
            >
              <Icon size={16} />
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

      {/* Main Content Grid */}
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
          {!searchQuery && (
            <button
              onClick={() => backfillMutation.mutate()}
              disabled={backfillMutation.isPending}
              className="mt-5 inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium shadow-sm transition"
            >
              <RefreshCw size={16} />
              Populate from Existing Reels
            </button>
          )}
        </div>
      ) : (
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
                      <span>{item.title}</span>
                      {item.url && (
                        <a
                          href={item.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-gray-400 hover:text-indigo-600 transition p-1"
                          title="Open Resource URL"
                        >
                          <ExternalLink size={15} />
                        </a>
                      )}
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
    </div>
  )
}
