import type { CSSProperties, ComponentType } from 'react';
import {
  DashboardOutlined,
  DatabaseOutlined,
  BgColorsOutlined,
  ExperimentOutlined,
  HddOutlined,
  ThunderboltOutlined,
  SettingOutlined,
  BookOutlined,
  FormOutlined,
  ShoppingCartOutlined,
  MessageOutlined,
  ApartmentOutlined,
  ScanOutlined,
  EyeOutlined,
  CalendarOutlined,
  ReadOutlined,
  RobotOutlined,
  BellOutlined,
  CrownOutlined,
} from '@ant-design/icons';
import type { PageId } from '@/context/AppContext';

export interface NavPageEntry {
  id: PageId;
  labelKey: string;
  iconKey: string;
  Icon: ComponentType<{ style?: CSSProperties }>;
}

/** Single source of truth for sidebar / topnav / command palette page navigation. */
export const NAV_PAGES: NavPageEntry[] = [
  { id: 'dashboard', iconKey: 'DashboardOutlined', Icon: DashboardOutlined, labelKey: 'dashboard' },
  { id: 'storage', iconKey: 'DatabaseOutlined', Icon: DatabaseOutlined, labelKey: 'storage' },
  { id: 'fluids', iconKey: 'BgColorsOutlined', Icon: BgColorsOutlined, labelKey: 'fluidsPage' },
  { id: 'essentia', iconKey: 'ExperimentOutlined', Icon: ExperimentOutlined, labelKey: 'essentiaPage' },
  { id: 'cpu', iconKey: 'HddOutlined', Icon: HddOutlined, labelKey: 'cpuPage' },
  { id: 'power', iconKey: 'ThunderboltOutlined', Icon: ThunderboltOutlined, labelKey: 'power' },
  { id: 'topology', iconKey: 'ApartmentOutlined', Icon: ApartmentOutlined, labelKey: 'topology' },
  { id: 'gtmachines', iconKey: 'SettingOutlined', Icon: SettingOutlined, labelKey: 'gtMachines' },
  { id: 'recipes', iconKey: 'BookOutlined', Icon: BookOutlined, labelKey: 'recipes' },
  { id: 'pattern', iconKey: 'FormOutlined', Icon: FormOutlined, labelKey: 'patternEditor' },
  { id: 'order', iconKey: 'ShoppingCartOutlined', Icon: ShoppingCartOutlined, labelKey: 'aeOrdering' },
  { id: 'chat', iconKey: 'MessageOutlined', Icon: MessageOutlined, labelKey: 'chat' },
  { id: 'linkscanner', iconKey: 'ScanOutlined', Icon: ScanOutlined, labelKey: 'linkScanner' },
  { id: 'monitorbindings', iconKey: 'EyeOutlined', Icon: EyeOutlined, labelKey: 'monitorBindings' },
  { id: 'planner', iconKey: 'CalendarOutlined', Icon: CalendarOutlined, labelKey: 'plannerPage' },
  { id: 'quests', iconKey: 'ReadOutlined', Icon: ReadOutlined, labelKey: 'questsPage' },
  { id: 'assistant', iconKey: 'RobotOutlined', Icon: RobotOutlined, labelKey: 'assistantPage' },
  { id: 'alertshistory', iconKey: 'BellOutlined', Icon: BellOutlined, labelKey: 'alertsHistoryPage' },
  { id: 'admin', iconKey: 'CrownOutlined', Icon: CrownOutlined, labelKey: 'adminPage' },
  { id: 'settings', iconKey: 'SettingOutlined', Icon: SettingOutlined, labelKey: 'settings' },
];

/** Legacy shape for callers that only need id + labelKey (+ icon string key). */
export const ALL_PAGES: Array<{ id: PageId; icon: string; labelKey: string }> = NAV_PAGES.map(
  (p) => ({
    id: p.id,
    icon: p.iconKey,
    labelKey: p.labelKey,
  })
);
